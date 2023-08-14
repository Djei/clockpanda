package djei.clockpanda.scheduling.optimization

sealed class EventError(message: String, override val cause: Throwable?) : Error(message, cause) {
    data class FromCalendarEventError(
        override val cause: Throwable
    ) : EventError("from calendar event error: ${cause.message ?: "unknown error"}", cause)
}
