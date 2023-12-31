package djei.clockpanda.repository

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.jooq.tables.references.USER
import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.LocalTimeSpan
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.testing.ClockPandaSpringBootTest
import djei.clockpanda.transaction.TransactionManager
import djei.clockpanda.transaction.TransactionalContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.willThrow
import org.springframework.beans.factory.annotation.Autowired

@ClockPandaSpringBootTest
class UserRepositoryTest {
    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var transactionManager: TransactionManager

    @Test
    fun `test getByEmail should return null if no user found`() {
        val result = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, "should_not_exist@github.com")
        }

        assertThat(result).isEqualTo(null.right())
    }

    @Test
    fun `test getByEmail should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER) } willThrow { exception }

        val result = userRepository.getByEmail(mockCtx, "should_not_exist@github.com")

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test list should return empty list if no user`() {
        val result = transactionManager.transaction { ctx ->
            userRepository.list(ctx)
        }

        assertThat(result).isEqualTo(emptyList<User>().right())
    }

    @Test
    fun `test list should return all users`() {
        val result = transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userRepository.create(ctx, UserFixtures.djei2WithPreferences)

            userRepository.list(ctx)
        }

        assertThat(result).isEqualTo(
            listOf(
                UserFixtures.djei1NoPreferences,
                UserFixtures.djei2WithPreferences
            ).right()
        )
    }

    @Test
    fun `test list should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER) } willThrow { exception }

        val result = userRepository.list(mockCtx)

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test create should create a user`() {
        val nullValuesUser = User(
            email = "djei@email.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = null,
            preferences = null,
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )
        val allValuesUser = User(
            email = "djei2@email.com",
            firstName = "Djei2 First Name",
            lastName = "Djei2 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )

        val nullValuesResult = transactionManager.transaction { ctx ->
            userRepository.create(ctx, nullValuesUser)
        }
        val allValuesResult = transactionManager.transaction { ctx ->
            userRepository.create(ctx, allValuesUser)
        }

        assertThat(nullValuesResult).isEqualTo(nullValuesUser.right())
        val fetchAfterCreateNullValuesUser = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, "djei@email.com")
        }
        assertThat(fetchAfterCreateNullValuesUser).isEqualTo(nullValuesUser.right())
        assertThat(allValuesResult).isEqualTo(allValuesUser.right())
        val fetchAfterCreateAllValuesUser = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, "djei2@email.com")
        }
        assertThat(fetchAfterCreateAllValuesUser).isEqualTo(allValuesUser.right())
    }

    @Test
    fun `test create should return left value if query fails`() {
        val allValuesUser = User(
            email = "djei2@email.com",
            firstName = "Djei2 First Name",
            lastName = "Djei2 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.insertInto(USER) } willThrow { exception }

        val result = userRepository.create(mockCtx, allValuesUser)

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test updateMetadata should update metadata column`() {
        val initialUser = User(
            email = "djei@email.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        val newWorkingHours = UserFixtures.workingHours.toMutableMap()
        newWorkingHours[DayOfWeek.MONDAY] = emptyList()
        val newUserPreferences = UserPreferences.Version1(
            preferredTimeZone = TimeZone.of("America/New_York"),
            workingHours = newWorkingHours,
            targetFocusTimeHoursPerWeek = 40,
            preferredFocusTimeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0))
        )

        transactionManager.transaction { ctx ->
            userRepository.create(ctx, initialUser)
            userRepository.updatePreferences(ctx, initialUser.email, newUserPreferences)
        }

        val fetchAfterUpdateUser = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, "djei@email.com")
        }
        when (fetchAfterUpdateUser) {
            is Either.Left -> fail("Fetch should succeed")
            is Either.Right -> {
                val updatedMetadata = fetchAfterUpdateUser.value!!.preferences
                assertThat(updatedMetadata!!.preferredTimeZone).isEqualTo(newUserPreferences.preferredTimeZone)
                assertThat(updatedMetadata.workingHours).isEqualTo(newUserPreferences.workingHours)
                assertThat(updatedMetadata.targetFocusTimeHoursPerWeek).isEqualTo(newUserPreferences.targetFocusTimeHoursPerWeek)
            }
        }
    }

    @Test
    fun `test updateMetadata should return left if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.update(USER) } willThrow { exception }

        val result = userRepository.updatePreferences(mockCtx, "does not matter", UserFixtures.userPreferences)

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test updateGoogleRefreshToken should update google refresh token column`() {
        val initialUser = User(
            email = "djei@email.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )

        transactionManager.transaction { ctx ->
            userRepository.create(ctx, initialUser)
            userRepository.updateGoogleRefreshToken(ctx, initialUser.email, "new-refresh-token")
        }

        val fetchAfterUpdateUser = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, "djei@email.com")
        }
        when (fetchAfterUpdateUser) {
            is Either.Left -> fail("Fetch should succeed")
            is Either.Right -> {
                assertThat(fetchAfterUpdateUser.value!!.googleRefreshToken).isEqualTo("new-refresh-token")
            }
        }
    }

    @Test
    fun `test updateGoogleRefreshToken should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.update(USER) } willThrow { exception }

        val result = userRepository.updateGoogleRefreshToken(mockCtx, "does not matter", "does not matter")

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test delete should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.deleteFrom(USER) } willThrow { exception }

        val result = userRepository.delete(mockCtx, "does not matter")

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test delete should delete user`() {
        val userToDelete = User(
            email = "djei@email.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        val userToKeep = User(
            email = "other_djei@email.com",
            firstName = "Other Djei First Name",
            lastName = "Other Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, userToDelete)
            userRepository.create(ctx, userToKeep)
        }

        transactionManager.transaction { ctx ->
            userRepository.delete(ctx, userToDelete.email)
        }

        val fetchAfterDeleteUserToDelete = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, userToDelete.email)
        }
        assertThat(fetchAfterDeleteUserToDelete).isEqualTo(null.right())
        val fetchAfterDeleteUserToKeep = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, userToKeep.email)
        }
        assertThat(fetchAfterDeleteUserToKeep.getOrElse { fail("should have succeeded") }).isNotNull
    }
}
