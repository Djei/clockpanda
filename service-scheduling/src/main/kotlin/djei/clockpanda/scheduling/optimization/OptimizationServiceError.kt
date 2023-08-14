package djei.clockpanda.scheduling.optimization

import djei.clockpanda.model.User

sealed class OptimizationServiceError(
    message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {
    data class UserRepositoryError(
        val userRepositoryError: djei.clockpanda.repository.UserRepositoryError
    ) : OptimizationServiceError(
        "user repository error: ${userRepositoryError.message ?: "unknown error"}",
        userRepositoryError
    )

    data class GoogleCalendarApiFacadeError(
        val googleCalendarApiFacadeError: djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacadeError
    ) : OptimizationServiceError(
        "google calendar api facade error: ${googleCalendarApiFacadeError.message ?: "unknown error"}",
        googleCalendarApiFacadeError
    )

    data class EventError(
        val eventError: djei.clockpanda.scheduling.optimization.EventError
    ) : OptimizationServiceError(
        "event error: ${eventError.message ?: "unknown error"}",
        eventError
    )

    data class UserHasNoPreferencesError(val primaryUser: User) : OptimizationServiceError(
        "user ${primaryUser.email} has no preferences"
    )

    data class SolverError(override val cause: Throwable) : OptimizationServiceError(
        "solver error: ${cause.message ?: "unknown error"}",
        cause
    )
}
