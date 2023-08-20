package djei.clockpanda.scheduling.model

import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade.Companion.EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_PERSONAL_TASK_ID_KEY
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade.Companion.EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.*
import kotlin.time.DurationUnit

sealed interface CalendarEvent {
    companion object {
        fun fromGoogleCalendarEvent(googleCalendarEvent: Event): CalendarEvent {
            if (googleCalendarEvent.start.dateTime != null && googleCalendarEvent.end.dateTime != null) {
                return InstantCalendarEvent(
                    id = googleCalendarEvent.id,
                    title = googleCalendarEvent.summary ?: "",
                    description = googleCalendarEvent.description ?: "",
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    type = CalendarEventType.fromExtendedProperties(googleCalendarEvent.extendedProperties),
                    iCalUid = googleCalendarEvent.iCalUID,
                    isRecurring = googleCalendarEvent.recurringEventId != null,
                    owner = googleCalendarEvent.organizer?.email ?: "unknown",
                    startTime = Instant.parse(googleCalendarEvent.start.dateTime.toStringRfc3339()),
                    endTime = Instant.parse(googleCalendarEvent.end.dateTime.toStringRfc3339()),
                    busy = googleCalendarEvent.transparency != "transparent",
                    attendees = googleCalendarEvent.attendees
                        ?.map(CalendarEventAttendee::fromGoogleCalendarAttendee)
                        ?: emptyList(),
                    personalTaskId = googleCalendarEvent.extendedProperties
                        ?.shared
                        ?.get(EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_PERSONAL_TASK_ID_KEY)
                )
            } else {
                return LocalDateCalendarEvent(
                    id = googleCalendarEvent.id,
                    title = googleCalendarEvent.summary ?: "",
                    description = googleCalendarEvent.description ?: "",
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    type = CalendarEventType.fromExtendedProperties(googleCalendarEvent.extendedProperties),
                    iCalUid = googleCalendarEvent.iCalUID,
                    isRecurring = googleCalendarEvent.recurringEventId != null,
                    owner = googleCalendarEvent.organizer?.email ?: "unknown",
                    startDate = LocalDate.parse(googleCalendarEvent.start.date.toStringRfc3339()),
                    endDate = LocalDate.parse(googleCalendarEvent.end.date.toStringRfc3339()),
                    busy = googleCalendarEvent.transparency != "transparent",
                    attendees = googleCalendarEvent.attendees
                        ?.map(CalendarEventAttendee::fromGoogleCalendarAttendee)
                        ?: emptyList(),
                    personalTaskId = googleCalendarEvent.extendedProperties
                        ?.shared
                        ?.get(EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_PERSONAL_TASK_ID_KEY)
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
    val attendees: List<CalendarEventAttendee>
    val personalTaskId: String?

    fun getTimeSpan(timeZone: TimeZone): TimeSpan

    fun getDurationInMinutes(timeZone: TimeZone): Int {
        val timeSpan = getTimeSpan(timeZone)
        return (timeSpan.end - timeSpan.start).toInt(DurationUnit.MINUTES)
    }

    fun getCalendarEventType(): CalendarEventType

    fun isUserAttending(userEmail: String): Boolean {
        return attendees.any { it.email == userEmail && it.attendanceStatus != CalendarEventAttendanceStatus.DECLINED }
    }
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
    override val attendees: List<CalendarEventAttendee>,
    override val personalTaskId: String?,
    private val startTime: Instant,
    private val endTime: Instant,
    private val type: CalendarEventType?
) : CalendarEvent {
    override fun getTimeSpan(timeZone: TimeZone): TimeSpan {
        return TimeSpan(
            start = startTime,
            end = endTime
        )
    }

    // Rely both on native type and title to determine the type of the event for backward compatibility
    // Can be removed in a future release version when all events have a native type
    // which should just happen naturally as the optimizer regularly runs for all users
    override fun getCalendarEventType(): CalendarEventType {
        return type ?: CalendarEventType.fromCalendarEventTitle(title)
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
    override val attendees: List<CalendarEventAttendee>,
    override val personalTaskId: String?,
    private val startDate: LocalDate,
    private val endDate: LocalDate,
    private val type: CalendarEventType?
) : CalendarEvent {
    override fun getTimeSpan(timeZone: TimeZone): TimeSpan {
        return TimeSpan(
            start = startDate.atStartOfDayIn(timeZone),
            end = endDate.atStartOfDayIn(timeZone)
        )
    }

    // Rely both on native type and title to determine the type of the event for backward compatibility
    // Can be removed in a future release version when all events have a native type
    // which should just happen naturally as the optimizer regularly runs for all users
    override fun getCalendarEventType(): CalendarEventType {
        return type ?: CalendarEventType.fromCalendarEventTitle(title)
    }
}

data class CalendarEventAttendee(
    val email: String,
    val attendanceStatus: CalendarEventAttendanceStatus
) {
    companion object {
        fun fromGoogleCalendarAttendee(attendee: EventAttendee): CalendarEventAttendee {
            return CalendarEventAttendee(
                email = attendee.email,
                attendanceStatus = CalendarEventAttendanceStatus.fromGoogleCalendarResponseStatus(attendee.responseStatus)
            )
        }
    }
}

enum class CalendarEventAttendanceStatus {
    ACCEPTED,
    DECLINED,
    MAYBE;

    companion object {
        fun fromGoogleCalendarResponseStatus(responseStatus: String): CalendarEventAttendanceStatus {
            return when (responseStatus) {
                "accepted" -> ACCEPTED
                "declined" -> DECLINED
                "maybe" -> MAYBE
                // Assume the strongest response possible in terms of blocking status i.e. ACCEPTED
                else -> ACCEPTED
            }
        }
    }
}

enum class CalendarEventType {
    FOCUS_TIME,
    PERSONAL_TASK,

    // External event is an event not created by Clock Panda
    // e.g. an event created directly on Google Calendar that does not respect our event categorization logic
    EXTERNAL_EVENT;

    companion object {
        fun fromCalendarEventTitle(title: String): CalendarEventType {
            if (title.contains(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE)) {
                return FOCUS_TIME
            }
            return EXTERNAL_EVENT
        }

        fun fromExtendedProperties(extendedProperties: Event.ExtendedProperties?): CalendarEventType? {
            if (extendedProperties?.shared == null) {
                return null
            }
            return when (extendedProperties.shared[EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY]) {
                FOCUS_TIME.name -> FOCUS_TIME
                PERSONAL_TASK.name -> PERSONAL_TASK
                else -> null
            }
        }
    }
}

private const val CLOCK_PANDA_EVENT_TITLE_PREFIX = "[ClockPanda]"
const val CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE = "$CLOCK_PANDA_EVENT_TITLE_PREFIX Focus Time"
