package djei.clockpanda.scheduling.optimization.fixtures

import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.scheduling.optimization.model.OptimizerEvent
import djei.clockpanda.scheduling.optimization.model.TimeGrain
import kotlinx.datetime.Instant
import java.util.*

class OptimizerEventFixtures {
    companion object {
        val externalOptimizerEvent = OptimizerEvent(
            id = CalendarEventFixtures.externalTypeCalendarEvent.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T00:00:00Z")),
            durationInTimeGrains = 4,
            type = CalendarEventType.EXTERNAL_EVENT,
            title = CalendarEventFixtures.externalTypeCalendarEvent.title,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = "user1",
            userPersonalTask = null
        )
        val updatedExistingFocusTime = OptimizerEvent(
            id = CalendarEventFixtures.focusTimeCalendarEvent1.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T15:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.FOCUS_TIME,
            title = CalendarEventFixtures.focusTimeCalendarEvent1.title,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent1,
            owner = "user1",
            userPersonalTask = null
        )
        val noChangeExistingFocusTime = OptimizerEvent(
            id = CalendarEventFixtures.focusTimeCalendarEvent3.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-01T00:00:00Z")),
            durationInTimeGrains = 12,
            type = CalendarEventType.FOCUS_TIME,
            title = CalendarEventFixtures.focusTimeCalendarEvent3.title,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent3,
            owner = "user1",
            userPersonalTask = null
        )
        val existingFocusTimeToBeDeleted = OptimizerEvent(
            id = CalendarEventFixtures.focusTimeCalendarEvent2.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-02T00:00:00Z")),
            durationInTimeGrains = 0,
            type = CalendarEventType.FOCUS_TIME,
            title = CalendarEventFixtures.focusTimeCalendarEvent2.title,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent2,
            owner = "user1",
            userPersonalTask = null
        )
        val newFocusTimeToBeCreated = OptimizerEvent(
            id = "new_focus_time_to_create",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-15T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            originalCalendarEvent = null,
            owner = "user1",
            userPersonalTask = UserPersonalTaskFixtures.djei1OneOffReviewTechDesignUserPersonalTask
        )
        val personalTaskToBeCreated = OptimizerEvent(
            id = "new_personal_task_to_create",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-16T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = null,
            owner = "user1",
            userPersonalTask = UserPersonalTaskFixtures.djei1OneOffReviewTechDesignUserPersonalTask
        )
        val existingPersonalTaskToBeDeleted = OptimizerEvent(
            id = CalendarEventFixtures.personalTaskCalendarEvent1.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 0,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent1,
            owner = "user1",
            userPersonalTask = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice
        )
        val updatedExistingPersonalTask = OptimizerEvent(
            id = CalendarEventFixtures.personalTaskCalendarEvent2.id,
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent2,
            owner = "user1",
            userPersonalTask = UserPersonalTaskFixtures.djei2OneOffReadPaperUserPersonalTask
        )
    }
}
