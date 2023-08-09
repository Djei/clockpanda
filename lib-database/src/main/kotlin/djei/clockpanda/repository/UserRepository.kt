package djei.clockpanda.repository

import djei.clockpanda.jooq.tables.references.USER
import djei.clockpanda.model.User
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository {

    fun fetchByEmail(ctx: DSLContext, email: String): User? {
        return ctx.selectFrom(USER)
            .where(USER.EMAIL.eq(email))
            .fetchOne()?.let { User.fromJooqRecord(it) }
    }

    fun create(ctx: DSLContext, user: User): User {
        ctx.insertInto(USER)
            .set(user.toJooqRecord())
            .execute()
        return user
    }

    fun updateGoogleRefreshToken(
        ctx: DSLContext,
        email: String,
        refreshTokenValue: String?
    ) {
        ctx.update(USER)
            .set(USER.GOOGLE_REFRESH_TOKEN, refreshTokenValue)
            .where(USER.EMAIL.eq(email))
            .execute()
    }
}
