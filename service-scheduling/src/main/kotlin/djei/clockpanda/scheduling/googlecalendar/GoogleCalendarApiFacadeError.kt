package djei.clockpanda.scheduling.googlecalendar

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

    data class GoogleCalendarApiDeleteEventError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError(
        "google calendar api delete event error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GoogleCalendarApiListEventsError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError("google calendar api list events error: ${cause.message ?: "unknown error"}")
}
