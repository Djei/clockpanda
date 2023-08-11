package djei.clockpanda.repository

import arrow.core.Either
import djei.clockpanda.jooq.tables.references.USER
import djei.clockpanda.model.User
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository {

    fun fetchByEmail(ctx: DSLContext, email: String): Either<UserRepositoryError, User?> {
        return Either.catch {
            ctx.selectFrom(USER)
                .where(USER.EMAIL.eq(email))
                .fetchOne()
        }.mapLeft { UserRepositoryError.DatabaseError(it.message) }
            .map { it?.let { User.fromJooqRecord(it) } }
    }

    fun create(ctx: DSLContext, user: User): Either<UserRepositoryError, User> {
        return Either.catch {
            ctx.insertInto(USER)
                .set(user.toJooqRecord())
                .execute()
        }.mapLeft { UserRepositoryError.DatabaseError(it.message) }
            .map { user }
    }

    fun updateGoogleRefreshToken(
        ctx: DSLContext,
        email: String,
        refreshTokenValue: String?
    ): Either<UserRepositoryError, Unit> {
        return Either.catch {
            ctx.update(USER)
                .set(USER.GOOGLE_REFRESH_TOKEN, refreshTokenValue)
                .where(USER.EMAIL.eq(email))
                .execute()
        }.mapLeft { UserRepositoryError.DatabaseError(it.message) }
            .map { Unit }
    }
}
