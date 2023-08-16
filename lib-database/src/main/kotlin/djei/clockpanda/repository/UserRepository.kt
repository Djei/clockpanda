package djei.clockpanda.repository

import arrow.core.Either
import djei.clockpanda.jooq.tables.references.USER
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.transaction.TransactionalContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class UserRepository {

    fun fetchByEmail(ctx: TransactionalContext, email: String): Either<UserRepositoryError, User?> {
        return Either.catch {
            ctx.selectFrom(USER)
                .where(USER.EMAIL.eq(email))
                .fetchOne()
        }.mapLeft { UserRepositoryError.DatabaseError(it) }
            .map { it?.let { User.fromJooqRecord(it) } }
    }

    fun list(ctx: TransactionalContext): Either<UserRepositoryError, List<User>> {
        return Either.catch {
            ctx.selectFrom(USER)
                .fetch()
                .map { User.fromJooqRecord(it) }
        }.mapLeft { UserRepositoryError.DatabaseError(it) }
    }

    fun create(ctx: TransactionalContext, user: User): Either<UserRepositoryError, User> {
        return Either.catch {
            ctx.insertInto(USER)
                .set(user.toJooqRecord())
                .execute()
        }.mapLeft { UserRepositoryError.DatabaseError(it) }
            .map { user }
    }

    fun updatePreferences(
        ctx: TransactionalContext,
        email: String,
        preferences: UserPreferences
    ): Either<UserRepositoryError, Unit> {
        return Either.catch {
            ctx.update(USER)
                .set(USER.PREFERENCES, preferences.toJooqData())
                .set(USER.LAST_UPDATED_AT, OffsetDateTime.now())
                .where(USER.EMAIL.eq(email))
                .execute()
        }.mapLeft { UserRepositoryError.DatabaseError(it) }
            .map { Unit }
    }

    fun updateGoogleRefreshToken(
        ctx: TransactionalContext,
        email: String,
        refreshTokenValue: String?
    ): Either<UserRepositoryError, Unit> {
        return Either.catch {
            ctx.update(USER)
                .set(USER.GOOGLE_REFRESH_TOKEN, refreshTokenValue)
                .set(USER.LAST_UPDATED_AT, OffsetDateTime.now())
                .where(USER.EMAIL.eq(email))
                .execute()
        }.mapLeft { UserRepositoryError.DatabaseError(it) }
            .map { Unit }
    }
}
