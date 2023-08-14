package djei.clockpanda.scheduling.model.fixtures

import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEvent
import kotlinx.datetime.Instant

class CalendarEventFixtures {
    companion object {
        val externalTypeCalendarEvent = CalendarEvent(
            id = "id_1",
            title = "external_type that does not match clock panda events",
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T01:00:00Z"),
            startDate = null,
            endDate = null,
            iCalUid = "unique_ical_uid_1",
            isRecurring = false,
            owner = "djei1@email.com"
        )

        val focusTimeCalendarEvent1 = CalendarEvent(
            id = "id_2",
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-01T00:00:00Z"),
            endTime = Instant.parse("2021-01-01T03:00:00Z"),
            startDate = null,
            endDate = null,
            iCalUid = "unique_ical_uid_2",
            isRecurring = false,
            owner = "djei1@email.com"
        )

        val focusTimeCalendarEvent2 = CalendarEvent(
            id = "id_3",
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            description = "description",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            startTime = Instant.parse("2021-01-02T00:00:00Z"),
            endTime = Instant.parse("2021-01-02T03:00:00Z"),
            startDate = null,
            endDate = null,
            iCalUid = "unique_ical_uid_3",
            isRecurring = false,
            owner = "djei1@email.com"
        )
    }
}
