package djei.clockpanda.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import djei.clockpanda.jooq.tables.references.USER
import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.testing.ClockPandaSpringBootTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.jooq.DSLContext
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
    lateinit var dslContext: DSLContext

    @Test
    fun `test fetchByEmail should return null if no user found`() {
        val result = userRepository.fetchByEmail(dslContext, "should_not_exist@github.com")

        assertThat(result).isEqualTo(null.right())
    }

    @Test
    fun `test fetchByEmail should return left value if query fails`() {
        val mockCtx: DSLContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER) } willThrow { exception }

        val result = userRepository.fetchByEmail(mockCtx, "should_not_exist@github.com")

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test create should create a user`() {
        val nullValuesUser = User(
            email = "djei@github.com",
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
            email = "djei2@github.com",
            firstName = "Djei2 First Name",
            lastName = "Djei2 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )

        val nullValuesResult = userRepository.create(dslContext, nullValuesUser)
        val allValuesResult = userRepository.create(dslContext, allValuesUser)

        assertThat(nullValuesResult).isEqualTo(nullValuesUser.right())
        val fetchAfterCreateNullValuesUser = userRepository.fetchByEmail(dslContext, "djei@github.com")
        assertThat(fetchAfterCreateNullValuesUser).isEqualTo(nullValuesUser.right())
        assertThat(allValuesResult).isEqualTo(allValuesUser.right())
        val fetchAfterCreateAllValuesUser = userRepository.fetchByEmail(dslContext, "djei2@github.com")
        assertThat(fetchAfterCreateAllValuesUser).isEqualTo(allValuesUser.right())
    }

    @Test
    fun `test create should return left value if query fails`() {
        val allValuesUser = User(
            email = "djei2@github.com",
            firstName = "Djei2 First Name",
            lastName = "Djei2 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        val mockCtx: DSLContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.insertInto(USER) } willThrow { exception }

        val result = userRepository.create(mockCtx, allValuesUser)

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test updateMetadata should update metadata column`() {
        val initialUser = User(
            email = "djei@github.com",
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
            targetFocusTimeHoursPerWeek = 40
        )

        userRepository.create(dslContext, initialUser)
        userRepository.updatePreferences(dslContext, initialUser.email, newUserPreferences)

        when (val fetchAfterUpdateUser = userRepository.fetchByEmail(dslContext, "djei@github.com")) {
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
        val mockCtx: DSLContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.update(USER) } willThrow { exception }

        val result = userRepository.updatePreferences(mockCtx, "does not matter", UserFixtures.userPreferences)

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test updateGoogleRefreshToken should update google refresh token column`() {
        val initialUser = User(
            email = "djei@github.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )

        userRepository.create(dslContext, initialUser)
        userRepository.updateGoogleRefreshToken(dslContext, initialUser.email, "new-refresh-token")

        when (val fetchAfterUpdateUser = userRepository.fetchByEmail(dslContext, "djei@github.com")) {
            is Either.Left -> fail("Fetch should succeed")
            is Either.Right -> {
                assertThat(fetchAfterUpdateUser.value!!.googleRefreshToken).isEqualTo("new-refresh-token")
            }
        }
    }

    @Test
    fun `test updateGoogleRefreshToken should return left value if query fails`() {
        val mockCtx: DSLContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.update(USER) } willThrow { exception }

        val result = userRepository.updateGoogleRefreshToken(mockCtx, "does not matter", "does not matter")

        assertThat(result).isEqualTo(UserRepositoryError.DatabaseError(exception).left())
    }
}
