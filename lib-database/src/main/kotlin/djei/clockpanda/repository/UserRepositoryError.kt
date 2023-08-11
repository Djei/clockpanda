package djei.clockpanda.repository

sealed class UserRepositoryError(message: String) : Error(message) {
    data class DatabaseError(val details: String?) :
        UserRepositoryError("database error: ${details ?: "unknown error"}")
}
