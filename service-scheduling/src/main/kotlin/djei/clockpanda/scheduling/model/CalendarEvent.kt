package djei.clockpanda.scheduling.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.api.services.calendar.model.Event
import djei.clockpanda.model.CalendarProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.DurationUnit

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val calendarProvider: CalendarProvider,
    private val startTime: Instant?,
    private val endTime: Instant?,
    private val startDate: LocalDate?,
    private val endDate: LocalDate?,
    val iCalUid: String,
    val isRecurring: Boolean,
    val owner: String
) {
    companion object {
        fun fromGoogleCalendarEvent(googleCalendarEvent: Event): CalendarEvent {
            return CalendarEvent(
                id = googleCalendarEvent.id,
                title = googleCalendarEvent.summary ?: "",
                description = googleCalendarEvent.description ?: "",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                startTime = googleCalendarEvent.start.dateTime?.let {
                    Instant.parse(googleCalendarEvent.start.dateTime.toStringRfc3339())
                },
                endTime = googleCalendarEvent.end.dateTime?.let {
                    Instant.parse(googleCalendarEvent.end.dateTime.toStringRfc3339())
                },
                startDate = googleCalendarEvent.start.date?.let {
                    LocalDate.parse(googleCalendarEvent.start.date.toStringRfc3339())
                },
                endDate = googleCalendarEvent.end.date?.let {
                    LocalDate.parse(googleCalendarEvent.end.date.toStringRfc3339())
                },
                iCalUid = googleCalendarEvent.iCalUID,
                isRecurring = googleCalendarEvent.recurringEventId != null,
                owner = googleCalendarEvent.organizer?.email ?: "unknown"
            )
        }
    }

    // This method simplifies figuring out the logic whether startDate/endDate or startTime/endTime should be used
    // startDate/endDate is used for all-day events and require to be interpreted in the user's timezone
    fun getTimeSpan(timeZone: TimeZone): Either<CalendarEventError, TimeSpan> {
        if (startDate != null && endDate != null) {
            return TimeSpan(
                start = startDate.atStartOfDayIn(timeZone),
                end = endDate.atStartOfDayIn(timeZone)
            ).right()
        } else if (startTime != null && endTime != null) {
            return TimeSpan(
                start = startTime,
                end = endTime
            ).right()
        }
        return CalendarEventError.GetTimeSpanError(
            IllegalStateException("CalendarEvent has neither startDate/endDate nor startTime/endTime")
        ).left()
    }

    fun getType(): CalendarEventType {
        return when (title) {
            CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE -> CalendarEventType.FOCUS_TIME
            else -> CalendarEventType.EXTERNAL_EVENT
        }
    }

    fun getDurationInMinutes(timeZone: TimeZone): Either<CalendarEventError, Int> {
        return getTimeSpan(timeZone).mapLeft {
            it
        }.map {
            (it.end - it.start).toInt(DurationUnit.MINUTES)
        }
    }
}

enum class CalendarEventType {
    FOCUS_TIME,

    // External event is an event not created by Clock Panda
    // e.g. an event created directly on Google Calendar that does not respect our event categorization logic
    EXTERNAL_EVENT
}

private const val CLOCK_PANDA_EVENT_TITLE_PREFIX = "[ClockPanda]"
const val CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE = "$CLOCK_PANDA_EVENT_TITLE_PREFIX Focus Time"
