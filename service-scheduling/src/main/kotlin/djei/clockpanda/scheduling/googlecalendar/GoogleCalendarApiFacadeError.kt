package djei.clockpanda.scheduling.googlecalendar

import djei.clockpanda.model.User

sealed class GoogleCalendarApiFacadeError(
    message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {
    data class GoogleAuthApiGetAccessTokenError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError(
        "google auth api get access token error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GoogleCalendarApiListCalendarListError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError(
        "google calendar api list calendar list error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GoogleCalendarApiListEventsError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError("google calendar api list events error: ${cause.message ?: "unknown error"}")

    data class GoogleCalendarApiNoPrimaryCalendarFoundForUserError(
        val user: User
    ) : GoogleCalendarApiFacadeError("no primary calendar found for user: ${user.email}")
}
