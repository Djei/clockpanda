package djei.clockpanda.scheduling.optimization.fixtures

import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.scheduling.optimization.Event
import djei.clockpanda.scheduling.optimization.TimeGrain
import kotlinx.datetime.Instant

class EventFixtures {
    companion object {
        val externalEvent = Event(
            id = CalendarEventFixtures.externalTypeCalendarEvent.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T00:00:00Z")),
            durationInTimeGrains = 4,
            type = CalendarEventType.EXTERNAL_EVENT,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = "user1"
        )
        val updatedExistingFocusTime = Event(
            id = CalendarEventFixtures.focusTimeCalendarEvent1.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T15:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.FOCUS_TIME,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent1,
            owner = "user1"
        )
        val noChangeExistingFocusTime = Event(
            id = CalendarEventFixtures.focusTimeCalendarEvent3.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T00:00:00Z")),
            durationInTimeGrains = 12,
            type = CalendarEventType.FOCUS_TIME,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent3,
            owner = "user1"
        )
        val existingFocusTimeToBeDeleted = Event(
            id = CalendarEventFixtures.focusTimeCalendarEvent2.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-02T00:00:00Z")),
            durationInTimeGrains = 0,
            type = CalendarEventType.FOCUS_TIME,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent2,
            owner = "user1"
        )
        val newFocusTimeToBeCreated = Event(
            id = "new_focus_time_to_create",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-15T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.FOCUS_TIME,
            originalCalendarEvent = null,
            owner = "user1"
        )
    }
}
