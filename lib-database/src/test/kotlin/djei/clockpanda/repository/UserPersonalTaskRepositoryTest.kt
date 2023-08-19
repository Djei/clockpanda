package djei.clockpanda.repository

import arrow.core.getOrElse
import arrow.core.left
import djei.clockpanda.jooq.tables.references.USER_PERSONAL_TASK
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
import djei.clockpanda.testing.ClockPandaSpringBootTest
import djei.clockpanda.transaction.TransactionManager
import djei.clockpanda.transaction.TransactionalContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.willThrow
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

@ClockPandaSpringBootTest
class UserPersonalTaskRepositoryTest {
    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var userPersonalTaskRepository: UserPersonalTaskRepository

    @Autowired
    lateinit var transactionManager: TransactionManager

    @BeforeEach
    fun setup() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userRepository.create(ctx, UserFixtures.djei2WithPreferences)
        }.getOrElse { fail("This should not fail", it) }
    }

    @Test
    fun `test getById should return null if id does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, UUID.randomUUID())
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).isNull()
    }

    @Test
    fun `test getById should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.getById(mockCtx, UUID.randomUUID())

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test listByUserEmail should return empty list if user has no personal task`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, "djei1@email.com")
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).hasSize(0)
    }

    @Test
    fun `test listByUserEmail should return personal task belonging to user when exist`() {
        transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice)
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask)
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask)
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei2WeeklyFocusTimeUserPersonalTask)
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, "djei1@email.com")
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).hasSize(3)
        Assertions.assertThat(result.map(UserPersonalTask::id)).containsExactly(
            UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id,
            UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.id,
            UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.id
        )
    }

    @Test
    fun `test listByUserEmail should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.listByUserEmail(mockCtx, "djei1@email.com")

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test upsertPersonalTask inserts one off personal task if entry does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice)
        }.getOrElse { fail("This should not fail", it) }

        val retrieveAfterUpsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, result.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterUpsert).isNotNull
        Assertions.assertThat(retrieveAfterUpsert!!.id)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id)
        Assertions.assertThat(retrieveAfterUpsert.userEmail)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.userEmail)
        Assertions.assertThat(retrieveAfterUpsert.title)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.title)
        Assertions.assertThat(retrieveAfterUpsert.description)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.description)
        Assertions.assertThat(retrieveAfterUpsert.priority)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.priority)
        Assertions.assertThat(retrieveAfterUpsert.metadata).isInstanceOf(
            UserPersonalTaskMetadata.OneOffTask::class.java
        )
        Assertions.assertThat(retrieveAfterUpsert.metadata)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.metadata)
        // The upserted item created_at should be greater than the fixture because upserts should override the created_at to the current time
        Assertions.assertThat(retrieveAfterUpsert.createdAt)
            .isGreaterThan(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.createdAt)
        Assertions.assertThat(retrieveAfterUpsert.lastUpdatedAt).isNull()
    }

    @Test
    fun `test upsertPersonalTask inserts daily personal task if entry does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask)
        }.getOrElse { fail("This should not fail", it) }

        val retrieveAfterUpsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, result.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterUpsert).isNotNull
        Assertions.assertThat(retrieveAfterUpsert!!.id)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.id)
        Assertions.assertThat(retrieveAfterUpsert.userEmail)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.userEmail)
        Assertions.assertThat(retrieveAfterUpsert.title)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.title)
        Assertions.assertThat(retrieveAfterUpsert.description)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.description)
        Assertions.assertThat(retrieveAfterUpsert.priority)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.priority)
        Assertions.assertThat(retrieveAfterUpsert.metadata).isInstanceOf(
            UserPersonalTaskMetadata.DailyTask::class.java
        )
        Assertions.assertThat(retrieveAfterUpsert.metadata)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.metadata)
        // The upserted item created_at should be greater than the fixture because upserts should override the created_at to the current time
        Assertions.assertThat(retrieveAfterUpsert.createdAt)
            .isGreaterThan(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.createdAt)
        Assertions.assertThat(retrieveAfterUpsert.lastUpdatedAt).isNull()
    }

    @Test
    fun `test upsertPersonalTask inserts weekly personal task if entry does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask)
        }.getOrElse { fail("This should not fail", it) }

        val retrieveAfterUpsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, result.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterUpsert).isNotNull
        Assertions.assertThat(retrieveAfterUpsert!!.id)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.id)
        Assertions.assertThat(retrieveAfterUpsert.userEmail)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.userEmail)
        Assertions.assertThat(retrieveAfterUpsert.title)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.title)
        Assertions.assertThat(retrieveAfterUpsert.description)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.description)
        Assertions.assertThat(retrieveAfterUpsert.priority)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.priority)
        Assertions.assertThat(retrieveAfterUpsert.metadata).isInstanceOf(
            UserPersonalTaskMetadata.WeeklyTask::class.java
        )
        Assertions.assertThat(retrieveAfterUpsert.metadata)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.metadata)
        // The upserted item created_at should be greater than the fixture because upserts should override the created_at to the current time
        Assertions.assertThat(retrieveAfterUpsert.createdAt)
            .isGreaterThan(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.createdAt)
        Assertions.assertThat(retrieveAfterUpsert.lastUpdatedAt).isNull()
    }

    @Test
    fun `test upsertPersonalTask updates if entry does not exist`() {
        val initialInsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(ctx, UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask)
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsertPersonalTask(
                ctx,
                initialInsert.copy(
                    title = "new title",
                    description = "new description"
                )
            )
        }.getOrElse { fail("This should not fail", it) }

        val retrieveAfterUpsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, result.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterUpsert).isNotNull
        Assertions.assertThat(retrieveAfterUpsert!!.id)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.id)
        Assertions.assertThat(retrieveAfterUpsert.userEmail)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.userEmail)
        Assertions.assertThat(retrieveAfterUpsert.title)
            .isEqualTo("new title")
        Assertions.assertThat(retrieveAfterUpsert.description)
            .isEqualTo("new description")
        Assertions.assertThat(retrieveAfterUpsert.priority)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.priority)
        Assertions.assertThat(retrieveAfterUpsert.metadata).isInstanceOf(
            UserPersonalTaskMetadata.WeeklyTask::class.java
        )
        Assertions.assertThat(retrieveAfterUpsert.metadata)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.metadata)
        // The upserted item created_at should be the same as initial inserted item since second upsert should not change the created_at
        Assertions.assertThat(retrieveAfterUpsert.createdAt)
            .isEqualTo(initialInsert.createdAt)
        Assertions.assertThat(retrieveAfterUpsert.lastUpdatedAt).isNotNull
    }

    @Test
    fun `test upsertPersonalTask should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.insertInto(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.upsertPersonalTask(
            mockCtx,
            UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask
        )

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }
}
