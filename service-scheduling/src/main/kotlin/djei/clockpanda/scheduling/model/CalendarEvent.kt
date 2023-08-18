package djei.clockpanda.scheduling.model

import com.google.api.services.calendar.model.Event
import djei.clockpanda.model.CalendarProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.DurationUnit

sealed interface CalendarEvent {
    companion object {
        fun fromGoogleCalendarEvent(googleCalendarEvent: Event): CalendarEvent {
            if (googleCalendarEvent.start.dateTime != null && googleCalendarEvent.end.dateTime != null) {
                googleCalendarEvent.transparency
                return InstantCalendarEvent(
                    id = googleCalendarEvent.id,
                    title = googleCalendarEvent.summary ?: "",
                    description = googleCalendarEvent.description ?: "",
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    iCalUid = googleCalendarEvent.iCalUID,
                    isRecurring = googleCalendarEvent.recurringEventId != null,
                    owner = googleCalendarEvent.organizer?.email ?: "unknown",
                    startTime = Instant.parse(googleCalendarEvent.start.dateTime.toStringRfc3339()),
                    endTime = Instant.parse(googleCalendarEvent.end.dateTime.toStringRfc3339()),
                    busy = googleCalendarEvent.transparency != "transparent"
                )
            } else {
                return LocalDateCalendarEvent(
                    id = googleCalendarEvent.id,
                    title = googleCalendarEvent.summary ?: "",
                    description = googleCalendarEvent.description ?: "",
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    iCalUid = googleCalendarEvent.iCalUID,
                    isRecurring = googleCalendarEvent.recurringEventId != null,
                    owner = googleCalendarEvent.organizer?.email ?: "unknown",
                    startDate = LocalDate.parse(googleCalendarEvent.start.date.toStringRfc3339()),
                    endDate = LocalDate.parse(googleCalendarEvent.end.date.toStringRfc3339()),
                    busy = googleCalendarEvent.transparency != "transparent"
                )
            }
        }
    }

    val id: String
    val title: String
    val description: String
    val calendarProvider: CalendarProvider
    val iCalUid: String
    val isRecurring: Boolean
    val owner: String
    val busy: Boolean

    fun getTimeSpan(timeZone: TimeZone): TimeSpan

    fun getType(): CalendarEventType {
        return when (title) {
            CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE -> CalendarEventType.FOCUS_TIME
            else -> CalendarEventType.EXTERNAL_EVENT
        }
    }

    fun getDurationInMinutes(timeZone: TimeZone): Int {
        val timeSpan = getTimeSpan(timeZone)
        return (timeSpan.end - timeSpan.start).toInt(DurationUnit.MINUTES)
    }

    data class InstantCalendarEvent(
        override val id: String,
        override val title: String,
        override val description: String,
        override val calendarProvider: CalendarProvider,
        override val iCalUid: String,
        override val isRecurring: Boolean,
        override val owner: String,
        override val busy: Boolean,
        private val startTime: Instant,
        private val endTime: Instant
    ) : CalendarEvent {
        override fun getTimeSpan(timeZone: TimeZone): TimeSpan {
            return TimeSpan(
                start = startTime,
                end = endTime
            )
        }
    }

    data class LocalDateCalendarEvent(
        override val id: String,
        override val title: String,
        override val description: String,
        override val calendarProvider: CalendarProvider,
        override val iCalUid: String,
        override val isRecurring: Boolean,
        override val owner: String,
        override val busy: Boolean,
        private val startDate: LocalDate,
        private val endDate: LocalDate
    ) : CalendarEvent {
        override fun getTimeSpan(timeZone: TimeZone): TimeSpan {
            return TimeSpan(
                start = startDate.atStartOfDayIn(timeZone),
                end = endDate.atStartOfDayIn(timeZone)
            )
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
