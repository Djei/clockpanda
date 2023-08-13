package djei.clockpanda.scheduling.model.fixtures

import djei.clockpanda.model.CalendarProvider
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
    }
}
