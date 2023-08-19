package djei.clockpanda.repository

sealed class UserPersonalTaskRepositoryError(
    override val message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {
    data class DatabaseError(
        override val cause: Throwable
    ) : UserPersonalTaskRepositoryError(
        "database error: ${cause.message ?: "unknown error"}",
        cause
    )
}
