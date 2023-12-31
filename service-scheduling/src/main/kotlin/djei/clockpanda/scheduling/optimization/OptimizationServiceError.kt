package djei.clockpanda.scheduling.optimization

import djei.clockpanda.model.User

sealed class OptimizationServiceError(
    override val message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {
    data class UserRepositoryError(
        val userRepositoryError: djei.clockpanda.repository.UserRepositoryError
    ) : OptimizationServiceError(
        "user repository error: ${userRepositoryError.message}",
        userRepositoryError
    )

    data class GoogleCalendarApiFacadeError(
        val googleCalendarApiFacadeError: djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacadeError
    ) : OptimizationServiceError(
        "google calendar api facade error: ${googleCalendarApiFacadeError.message}",
        googleCalendarApiFacadeError
    )

    data class UserHasNoPreferencesError(val primaryUser: User) : OptimizationServiceError(
        "user ${primaryUser.email} has no preferences"
    )

    data class SolverError(override val cause: Throwable) : OptimizationServiceError(
        "solver error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class UserPersonalTaskRepositoryError(
        val userPersonalTaskRepositoryError: djei.clockpanda.repository.UserPersonalTaskRepositoryError
    ) : OptimizationServiceError(
        "user repository error: ${userPersonalTaskRepositoryError.message}",
        userPersonalTaskRepositoryError
    )
}
