package djei.clockpanda.repository

sealed class UserRepositoryError(
    override val message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {

    data class DatabaseError(
        override val cause: Throwable
    ) : UserRepositoryError(
        "database error: ${cause.message ?: "unknown error"}",
        cause
    )
}
