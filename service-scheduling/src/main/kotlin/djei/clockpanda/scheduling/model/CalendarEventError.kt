package djei.clockpanda.scheduling.model

sealed class CalendarEventError(
    override val message: String,
    override val cause: Throwable? = null
) : Error(message, cause) {
    data class ToGoogleCalendarEventForUpdateError(
        override val cause: Throwable
    ) : CalendarEventError(
        "to google calendar event for update error: ${cause.message ?: "unknown error"}",
        cause
    )

    data class GetTimeSpanError(
        override val cause: Throwable
    ) : CalendarEventError(
        "get time span error: ${cause.message ?: "unknown error"}",
        cause
    )
}
