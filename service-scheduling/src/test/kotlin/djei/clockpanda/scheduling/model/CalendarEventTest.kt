package djei.clockpanda.scheduling.model

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade.Companion.EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CalendarEventTest {

    @Test
    fun `test fromGoogleCalendarEvent`() {
        val dateTimeAllFields = CalendarEvent.fromGoogleCalendarEvent(
            Event()
                .setId("id")
                .setSummary(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE)
                .setDescription("description")
                .setICalUID("ical_uid")
                .setRecurringEventId("recurring_event_id")
                .setOrganizer(
                    Event.Organizer()
                        .setEmail("organizer_email")
                )
                .setStart(EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-01T00:00:00Z")))
                .setEnd(EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-01T02:00:00Z")))
                .setTransparency("transparent")
                .setAttendees(
                    listOf(
                        EventAttendee()
                            .setEmail("attendee_email")
                            .setResponseStatus("accepted")
                    )
                )
                .setExtendedProperties(
                    Event.ExtendedProperties().setShared(
                        mutableMapOf(
                            EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY to CalendarEventType.FOCUS_TIME.name
                        )
                    )
                )
        )
        val dateTimeNullFields = CalendarEvent.fromGoogleCalendarEvent(
            Event()
                .setId("id")
                .setSummary(null)
                .setDescription(null)
                .setICalUID("ical_uid")
                .setRecurringEventId(null)
                .setOrganizer(null)
                .setStart(EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-01T00:00:00Z")))
                .setEnd(EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-01T02:00:00Z")))
                .setTransparency("opaque")
                .setAttendees(null)
                .setExtendedProperties(
                    Event.ExtendedProperties().setPrivate(
                        mutableMapOf(
                            EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY to CalendarEventType.FOCUS_TIME.name
                        )
                    )
                )
        )
        val localDateAllFields = CalendarEvent.fromGoogleCalendarEvent(
            Event()
                .setId("id")
                .setSummary(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE)
                .setDescription("description")
                .setICalUID("ical_uid")
                .setRecurringEventId("recurring_event_id")
                .setOrganizer(
                    Event.Organizer()
                        .setEmail("organizer_email")
                )
                .setStart(EventDateTime().setDate(DateTime.parseRfc3339("2021-01-01")))
                .setEnd(EventDateTime().setDate(DateTime.parseRfc3339("2021-01-02")))
                .setTransparency("transparent")
                .setAttendees(
                    listOf(
                        EventAttendee()
                            .setEmail("attendee_email")
                            .setResponseStatus("accepted")
                    )
                )
        )
        val localDateNullFields = CalendarEvent.fromGoogleCalendarEvent(
            Event()
                .setId("id")
                .setSummary(null)
                .setDescription(null)
                .setICalUID("ical_uid")
                .setRecurringEventId(null)
                .setOrganizer(null)
                .setStart(EventDateTime().setDate(DateTime.parseRfc3339("2021-03-28")))
                .setEnd(EventDateTime().setDate(DateTime.parseRfc3339("2021-03-29")))
                .setTransparency("opaque")
                .setAttendees(null)
        )

        assertThat(dateTimeAllFields).isEqualTo(
            InstantCalendarEvent(
                id = "id",
                title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
                description = "description",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                type = CalendarEventType.FOCUS_TIME,
                iCalUid = "ical_uid",
                isRecurring = true,
                owner = "organizer_email",
                busy = false,
                startTime = Instant.parse("2021-01-01T00:00:00Z"),
                endTime = Instant.parse("2021-01-01T02:00:00Z"),
                attendees = listOf(
                    CalendarEventAttendee(
                        email = "attendee_email",
                        attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                    )
                ),
                personalTaskId = null
            )
        )
        assertThat(dateTimeAllFields.getDurationInMinutes(TimeZone.UTC)).isEqualTo(120)
        assertThat(dateTimeNullFields).isEqualTo(
            InstantCalendarEvent(
                id = "id",
                title = "",
                description = "",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                type = null,
                iCalUid = "ical_uid",
                isRecurring = false,
                owner = "unknown",
                busy = true,
                startTime = Instant.parse("2021-01-01T00:00:00Z"),
                endTime = Instant.parse("2021-01-01T02:00:00Z"),
                attendees = emptyList(),
                personalTaskId = null
            )
        )
        assertThat(dateTimeNullFields.getDurationInMinutes(TimeZone.UTC)).isEqualTo(120)
        assertThat(localDateAllFields).isEqualTo(
            LocalDateCalendarEvent(
                id = "id",
                title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
                description = "description",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                type = null,
                iCalUid = "ical_uid",
                isRecurring = true,
                owner = "organizer_email",
                busy = false,
                startDate = LocalDate.parse("2021-01-01"),
                endDate = LocalDate.parse("2021-01-02"),
                attendees = listOf(
                    CalendarEventAttendee(
                        email = "attendee_email",
                        attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                    )
                ),
                personalTaskId = null
            )
        )
        assertThat(localDateAllFields.getDurationInMinutes(TimeZone.UTC)).isEqualTo(60 * 24)
        assertThat(localDateNullFields).isEqualTo(
            LocalDateCalendarEvent(
                id = "id",
                title = "",
                description = "",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                type = null,
                iCalUid = "ical_uid",
                isRecurring = false,
                owner = "unknown",
                busy = true,
                startDate = LocalDate.parse("2021-03-28"),
                endDate = LocalDate.parse("2021-03-29"),
                attendees = emptyList(),
                personalTaskId = null
            )
        )
        // Notice how there is only 23 hours of duration because of daylight savings
        assertThat(localDateNullFields.getDurationInMinutes(TimeZone.of("Europe/London"))).isEqualTo(60 * 23)
    }

    @Test
    fun `test fromGoogleCalendarResponseStatus`() {
        assertThat(CalendarEventAttendanceStatus.fromGoogleCalendarResponseStatus("accepted")).isEqualTo(
            CalendarEventAttendanceStatus.ACCEPTED
        )
        assertThat(CalendarEventAttendanceStatus.fromGoogleCalendarResponseStatus("declined")).isEqualTo(
            CalendarEventAttendanceStatus.DECLINED
        )
        assertThat(CalendarEventAttendanceStatus.fromGoogleCalendarResponseStatus("maybe")).isEqualTo(
            CalendarEventAttendanceStatus.MAYBE
        )
        assertThat(CalendarEventAttendanceStatus.fromGoogleCalendarResponseStatus("unknown")).isEqualTo(
            CalendarEventAttendanceStatus.ACCEPTED
        )
    }
}
