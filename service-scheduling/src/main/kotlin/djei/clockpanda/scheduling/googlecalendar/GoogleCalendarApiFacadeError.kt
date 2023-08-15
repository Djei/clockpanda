package djei.clockpanda.scheduling.googlecalendar

sealed class GoogleCalendarApiFacadeError(
    override val message: String,
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

    data class GoogleCalendarApiCreateEventError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError(
        "google calendar api create event error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GoogleCalendarApiUpdateEventError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError(
        "google calendar api update event error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GoogleCalendarApiListEventsError(
        override val cause: Throwable
    ) : GoogleCalendarApiFacadeError("google calendar api list events error: ${cause.message ?: "unknown error"}")

    data class NotAllowedToDeleteExternalEventError(
        val externalEventId: String
    ) : GoogleCalendarApiFacadeError("google calendar api not allowed to delete external event: $externalEventId")

    data class NotAllowedToUpdateExternalEventError(
        val externalEventId: String
    ) : GoogleCalendarApiFacadeError("google calendar api not allowed to update external event: $externalEventId")

    data class CalendarEventError(
        val calendarEventError: djei.clockpanda.scheduling.model.CalendarEventError
    ) : GoogleCalendarApiFacadeError("calendar event error: ${calendarEventError.message}", calendarEventError.cause)
}
