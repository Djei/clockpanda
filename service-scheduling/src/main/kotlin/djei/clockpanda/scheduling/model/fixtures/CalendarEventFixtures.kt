package djei.clockpanda.scheduling.model.fixtures

import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventAttendanceStatus
import djei.clockpanda.scheduling.model.CalendarEventAttendee
import djei.clockpanda.scheduling.model.InstantCalendarEvent
import kotlinx.datetime.Instant

class CalendarEventFixtures {
    companion object {
        val freeExternalTypeCalendarEvent = InstantCalendarEvent(
            id = "id_0",
            title = "external_type that does not match clock panda events",
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T01:00:00Z"),
            iCalUid = "unique_ical_uid_0",
            isRecurring = false,
            owner = "djei1@email.com",
            busy = false,
            attendees = listOf(
                CalendarEventAttendee(
                    email = "djei1@email.com",
                    attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                )
            )
        )

        val externalTypeCalendarEvent = InstantCalendarEvent(
            id = "id_1",
            title = "external_type that does not match clock panda events",
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T01:00:00Z"),
            iCalUid = "unique_ical_uid_1",
            isRecurring = false,
            owner = "djei1@email.com",
            busy = true,
            attendees = listOf(
                CalendarEventAttendee(
                    email = "djei1@email.com",
                    attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                )
            )
        )

        val focusTimeCalendarEvent1 = InstantCalendarEvent(
            id = "id_2",
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T03:00:00Z"),
            iCalUid = "unique_ical_uid_2",
            isRecurring = false,
            owner = "djei1@email.com",
            busy = true,
            attendees = listOf(
                CalendarEventAttendee(
                    email = "djei1@email.com",
                    attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                )
            )
        )

        val focusTimeCalendarEvent2 = InstantCalendarEvent(
            id = "id_3",
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-02T00:00:00Z"),
            endTime = Instant.parse("2021-01-02T03:00:00Z"),
            iCalUid = "unique_ical_uid_3",
            isRecurring = false,
            owner = "djei1@email.com",
            busy = true,
            attendees = listOf(
                CalendarEventAttendee(
                    email = "djei1@email.com",
                    attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                )
            )
        )

        val focusTimeCalendarEvent3 = InstantCalendarEvent(
            id = "id_3",
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T03:00:00Z"),
            iCalUid = "unique_ical_uid_3",
            isRecurring = false,
            owner = "djei1@email.com",
            busy = true,
            attendees = listOf(
                CalendarEventAttendee(
                    email = "djei1@email.com",
                    attendanceStatus = CalendarEventAttendanceStatus.ACCEPTED
                )
            )
        )
    }
}
