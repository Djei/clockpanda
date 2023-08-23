package djei.clockpanda.repository

import arrow.core.Either
import arrow.core.getOrElse
import djei.clockpanda.jooq.tables.references.USER_PERSONAL_TASK
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.transaction.TransactionalContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UserPersonalTaskRepository {
    fun getById(
        ctx: TransactionalContext,
        id: UUID
    ): Either<UserPersonalTaskRepositoryError, UserPersonalTask?> {
        return Either.catch {
            val result = ctx.selectFrom(USER_PERSONAL_TASK)
                .where(USER_PERSONAL_TASK.ID.eq(id))
                .fetchOne()
            result?.let { UserPersonalTask.fromJooqRecord(result) }
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }

    fun listByUserEmail(
        ctx: TransactionalContext,
        userEmail: String
    ): Either<UserPersonalTaskRepositoryError, List<UserPersonalTask>> {
        return Either.catch {
            ctx.selectFrom(USER_PERSONAL_TASK)
                .where(USER_PERSONAL_TASK.USER_EMAIL.eq(userEmail))
                .fetch()
                .map { UserPersonalTask.fromJooqRecord(it) }
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }

    fun upsert(
        ctx: TransactionalContext,
        userPersonalTask: UserPersonalTask,
        upsertedAt: Instant = Clock.System.now()
    ): Either<UserPersonalTaskRepositoryError, UserPersonalTask> {
        return Either.catch {
            ctx.insertInto(USER_PERSONAL_TASK)
                .set(
                    userPersonalTask
                        .copy(createdAt = upsertedAt, lastUpdatedAt = null)
                        .toJooqRecord()
                )
                .onConflict(USER_PERSONAL_TASK.ID)
                .doUpdate()
                .set(
                    userPersonalTask
                        .copy(lastUpdatedAt = upsertedAt)
                        .toJooqRecord()
                )
                .execute()
            getById(ctx, userPersonalTask.id).getOrElse { throw it }!!
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }

    fun updateOneOffPersonalTaskCurrentScheduledAt(
        ctx: TransactionalContext,
        personalTaskId: UUID,
        newCurrentScheduledAt: Instant
    ): Either<UserPersonalTaskRepositoryError, Unit> {
        return Either.catch {
            val task = getById(ctx, personalTaskId)
                .getOrElse { throw it }
            if (task == null) {
                Unit
            } else {
                val metadataWithUpdatedCurrentScheduledAt = (task.metadata as UserPersonalTaskMetadata.OneOffTask).copy(
                    currentScheduledAt = newCurrentScheduledAt
                )
                val newTask = task.copy(
                    metadata = metadataWithUpdatedCurrentScheduledAt,
                    lastUpdatedAt = Clock.System.now()
                )
                upsert(ctx, newTask)
                    .getOrElse { throw it }
                Unit
            }
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }

    fun delete(
        ctx: TransactionalContext,
        id: UUID
    ): Either<UserPersonalTaskRepositoryError, Unit> {
        return Either.catch {
            ctx.deleteFrom(USER_PERSONAL_TASK)
                .where(USER_PERSONAL_TASK.ID.eq(id))
                .execute()
            Unit
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }

    fun deleteByUserEmail(
        ctx: TransactionalContext,
        userEmail: String
    ): Either<UserPersonalTaskRepositoryError, Unit> {
        return Either.catch {
            ctx.deleteFrom(USER_PERSONAL_TASK)
                .where(USER_PERSONAL_TASK.USER_EMAIL.eq(userEmail))
                .execute()
            Unit
        }.mapLeft {
            UserPersonalTaskRepositoryError.DatabaseError(it)
        }
    }
}
