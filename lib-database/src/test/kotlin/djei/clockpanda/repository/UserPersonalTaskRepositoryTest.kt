package djei.clockpanda.repository

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.jooq.tables.references.USER_PERSONAL_TASK
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
import djei.clockpanda.testing.ClockPandaSpringBootTest
import djei.clockpanda.transaction.TransactionManager
import djei.clockpanda.transaction.TransactionalContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice
            )
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
            )
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei2OneOffReadPaperUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, "djei1@email.com")
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).hasSize(2)
        Assertions.assertThat(result.map(UserPersonalTask::id)).containsExactly(
            UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id,
            UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask.id
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
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice
            )
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
    fun `test upsertPersonalTask updates if entry does not exist`() {
        val initialInsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
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
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask.id)
        Assertions.assertThat(retrieveAfterUpsert.userEmail)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask.userEmail)
        Assertions.assertThat(retrieveAfterUpsert.title)
            .isEqualTo("new title")
        Assertions.assertThat(retrieveAfterUpsert.description)
            .isEqualTo("new description")
        Assertions.assertThat(retrieveAfterUpsert.metadata).isInstanceOf(
            UserPersonalTaskMetadata.OneOffTask::class.java
        )
        Assertions.assertThat(retrieveAfterUpsert.metadata)
            .isEqualTo(UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask.metadata)
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

        val result = userPersonalTaskRepository.upsert(
            mockCtx,
            UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
        )

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test deletePersonalTask if entry does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.delete(
                ctx,
                UUID.randomUUID()
            )
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).isEqualTo(Unit)
    }

    @Test
    fun `test updateOneOffPersonalTaskCurrentScheduledAt on task that does not exist`() {
        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.updateOneOffPersonalTaskCurrentScheduledAt(
                ctx,
                UUID.randomUUID(),
                Clock.System.now()
            )
        }

        Assertions.assertThat(result).isEqualTo(Unit.right())
    }

    @Test
    fun `test updateOneOffPersonalTaskCurrentScheduledAt on task that exist`() {
        val initialInsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.updateOneOffPersonalTaskCurrentScheduledAt(
                ctx,
                initialInsert.id,
                Instant.parse("2121-05-05T00:00:00Z")
            )
        }

        Assertions.assertThat(result).isEqualTo(Unit.right())
        val retrieveAfterUpdate = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, initialInsert.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat((retrieveAfterUpdate!!.metadata as UserPersonalTaskMetadata.OneOffTask).currentScheduledAt)
            .isEqualTo(Instant.parse("2121-05-05T00:00:00Z"))
    }

    @Test
    fun `test updateOneOffPersonalTaskCurrentScheduledAt should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.selectFrom(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.updateOneOffPersonalTaskCurrentScheduledAt(
            mockCtx,
            UUID.randomUUID(),
            Clock.System.now()
        )

        Assertions.assertThat(result).isEqualTo(
            UserPersonalTaskRepositoryError.DatabaseError(
                UserPersonalTaskRepositoryError.DatabaseError(exception)
            ).left()
        )
    }

    @Test
    fun `test deletePersonalTask if entry exists`() {
        val initialInsert = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.delete(
                ctx,
                initialInsert.id
            )
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).isEqualTo(Unit)
        val retrieveAfterDelete = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, initialInsert.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterDelete).isNull()
    }

    @Test
    fun `test deletePersonalTask should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.deleteFrom(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.delete(
            mockCtx,
            UUID.randomUUID()
        )

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }

    @Test
    fun `test deletePersonalTaskByUserEmail deletes all personal tasks of user`() {
        val initialInsert1 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDoExpensesUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }
        val initialInsert2 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffReviewTechDesignUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }
        val initialInsert3 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.upsert(
                ctx,
                UserPersonalTaskFixtures.djei2OneOffReadPaperUserPersonalTask
            )
        }.getOrElse { fail("This should not fail", it) }

        val result = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.deleteByUserEmail(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffReviewTechDesignUserPersonalTask.userEmail
            )
        }.getOrElse { fail("This should not fail", it) }

        Assertions.assertThat(result).isEqualTo(Unit)
        val retrieveAfterDelete1 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, initialInsert1.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterDelete1).isNull()
        val retrieveAfterDelete2 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, initialInsert2.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterDelete2).isNull()
        val retrieveAfterDelete3 = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, initialInsert3.id)
        }.getOrElse { fail("This should not fail", it) }
        Assertions.assertThat(retrieveAfterDelete3).isNotNull
    }

    @Test
    fun `test deletePersonalTaskByUserEmail should return left value if query fails`() {
        val mockCtx: TransactionalContext = mock()
        val exception = RuntimeException("some error")
        given { mockCtx.deleteFrom(USER_PERSONAL_TASK) } willThrow { exception }

        val result = userPersonalTaskRepository.deleteByUserEmail(
            mockCtx,
            "user@email.com"
        )

        Assertions.assertThat(result).isEqualTo(UserPersonalTaskRepositoryError.DatabaseError(exception).left())
    }
}
