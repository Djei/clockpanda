package djei.clockpanda.scheduling.model

sealed class CalendarEventError(message: String, override val cause: Throwable? = null) : Error(message, cause) {
    data class GetTimeSpanError(
        override val cause: Throwable
    ) : CalendarEventError(
        "get time span error: ${cause.message ?: "unknown error"}",
        cause
    )
}
