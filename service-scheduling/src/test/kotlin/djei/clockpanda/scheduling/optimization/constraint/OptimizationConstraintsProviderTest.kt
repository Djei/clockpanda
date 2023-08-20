package djei.clockpanda.scheduling.optimization.constraint

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier
import djei.clockpanda.model.LocalTimeSpan
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import djei.clockpanda.scheduling.optimization.model.OptimizerEvent
import djei.clockpanda.scheduling.optimization.model.TimeGrain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

class OptimizationConstraintsProviderTest {
    private val constraintVerifier = ConstraintVerifier.build(
        OptimizationConstraintsProvider(),
        OptimizationProblem::class.java,
        OptimizerEvent::class.java
    )

    @Test
    fun `penalize if focus time event overlaps with other events`() {
        val focusTime1 = OptimizerEvent(
            id = "focus-time-1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T02:00:00Z
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val focusTime2 = OptimizerEvent(
            id = "focus-time-2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:45:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T03:45:00Z
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        // External event 1 partially overlaps with focus time
        val externalOptimizerEvent1 = OptimizerEvent(
            id = "6fpknj2tjkc2ee2v32q7ve8t04",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T03:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T05:00:00Z
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        // External event 2 does not overlap with focus time but with external event 1 (which should not be penalized)
        val externalOptimizerEvent2 = OptimizerEvent(
            id = "bpokgerzlkc2ee2v32asdlklw",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 2",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T04:45:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T06:45:00Z
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        // External event 3 is completely contained within focus time
        val externalOptimizerEvent3 = OptimizerEvent(
            id = "asdqwejkasdjo9i3jodas236",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 3",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T02:00:00Z")
            ),
            durationInTimeGrains = 4, // End time: 2021-01-01T03:00:00Z
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        // External event 4 completely contains focus time
        val externalOptimizerEvent4 = OptimizerEvent(
            id = "asdjoilkjio3940dsfoj312",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 4",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:30:00Z")
            ),
            durationInTimeGrains = 24, // End time: 2021-01-01T07:30:00Z
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                focusTime1,
                focusTime2,
                externalOptimizerEvent1,
                externalOptimizerEvent2,
                externalOptimizerEvent3,
                externalOptimizerEvent4
            ),
            users = listOf(UserFixtures.djei2WithPreferences)
        )

        // Following overlaps are penalized:
        // - focusTime1 partially overlaps with focusTime2 - 15 mins
        // - externalEvent1 partially overlaps with focusTime2 - 45 mins
        // - externalEvent3 is fully contained within focusTime2 - 60 mins
        // - externalEvent4 fully contains focusTime2 - 120 mins
        // - externalEvent4 partially overlaps with focusTime1 - 30 mins
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::clockPandaEventsShouldNotOverlapWithOtherEvents)
            .givenSolution(solution)
            .penalizesBy(270)
    }

    @Test
    fun `penalize if focus time is outside of user preferences working hours`() {
        val focusTimeOutsideWorkingHours = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T08:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val focusTimeInsideWorkingHours = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T14:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(focusTimeOutsideWorkingHours, focusTimeInsideWorkingHours),
            users = listOf(UserFixtures.djei2WithPreferences)
        )

        // Focus time 1 is outside of working hours and penalized for 60 minutes
        // Focus time 2 is inside of working hours and not penalized
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::clockPandaEventsShouldNotBeOutsideOfWorkingHours)
            .givenSolution(solution)
            .penalizesBy(60)
    }

    @Test
    fun `penalize if focus time start and end are not on the same day (in user preferred timezone)`() {
        val userPreferenceWithEuropeLondonPreferredTimezone = UserFixtures.userPreferences.copy(
            preferredTimeZone = TimeZone.of("Europe/London")
        )
        val user = UserFixtures.djei2WithPreferences.copy(
            preferences = userPreferenceWithEuropeLondonPreferredTimezone
        )
        val focusTimeWithStartAndEndDateNotSameDay = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T23:00:00Z")
            ),
            durationInTimeGrains = 24,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val focusTimeNotPassingInUserTimeZone = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-07-07T23:45:00Z")
            ),
            durationInTimeGrains = 2,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val externalOptimizerEventDoesNotCount = OptimizerEvent(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T23:00:00Z")
            ),
            durationInTimeGrains = 24,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                focusTimeWithStartAndEndDateNotSameDay,
                focusTimeNotPassingInUserTimeZone,
                externalOptimizerEventDoesNotCount
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::clockPandaEventsShouldStartAndEndOnTheSameDay)
            .givenSolution(solution)
            .penalizesBy(1)
    }

    @Test
    fun `penalize if personal task does not have exactly the target duration`() {
        val user = UserFixtures.djei2WithPreferences
        val eventExceedingTargetDuration = OptimizerEvent(
            id = "exceeding",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent2,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent2.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = true
        )
        val eventZeroTargetDuration = OptimizerEvent(
            id = "zero",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 0,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent1,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent1.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = true
        )
        val eventNotEnoughTargetDuration = OptimizerEvent(
            id = "not_enough",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 1,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent1,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent1.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = true
        )
        val eventMeetingTargetDuration = OptimizerEvent(
            id = "meeting",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 4,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent1,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent1.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = true
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                eventMeetingTargetDuration,
                eventNotEnoughTargetDuration,
                eventZeroTargetDuration,
                eventExceedingTargetDuration
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::personalTasksShouldHaveExactlyTheirTargetDuration)
            .givenSolution(solution)
            .penalizesBy(105)
    }

    @Test
    fun `penalize according to personal task scoring`() {
        val user = UserFixtures.djei2WithPreferences
        val eventExceedingTargetDuration = OptimizerEvent(
            id = "exceeding",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 8,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent2,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent2.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = false
        )
        val eventNotEnoughTargetDuration = OptimizerEvent(
            id = "not_enough",
            startTimeGrain = TimeGrain(Instant.parse("2021-01-18T00:00:00Z")),
            durationInTimeGrains = 1,
            type = CalendarEventType.PERSONAL_TASK,
            title = "personal task",
            originalCalendarEvent = CalendarEventFixtures.personalTaskCalendarEvent1,
            owner = "user1",
            personalTaskId = CalendarEventFixtures.personalTaskCalendarEvent1.personalTaskId,
            personalTaskTargetDurationInMinutes = 60,
            isHighPriorityPersonalTask = true
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                eventNotEnoughTargetDuration,
                eventExceedingTargetDuration
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::personalTasksDurationScoring)
            .givenSolution(solution)
            .penalizesBy(45 * 24000 + 60 * 1200)
    }

    @Test
    fun `penalize if total focus time does not meet user target`() {
        // userPreferences is set to preferred focus time range 14:00 - 17:00
        val userPreferences = UserFixtures.userPreferences.copy(
            targetFocusTimeHoursPerWeek = 20
        )
        val user = UserFixtures.djei2WithPreferences.copy(
            preferences = userPreferences
        )
        val threeHoursOfFocusTime = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 12,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val fourHoursOfFocusTime = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-02T00:00:00Z")
            ),
            durationInTimeGrains = 16,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val externalOptimizerEvent1 = OptimizerEvent(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-03T00:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(threeHoursOfFocusTime, fourHoursOfFocusTime, externalOptimizerEvent1),
            users = listOf(UserFixtures.djei2WithPreferences)
        )

        // User preference wants 20 hours of focus time per week
        // Week 1 from 2020-12-28T00:00:00Z to 2021-01-04T00:00:00Z
        // Week 2 from 2021-01-04T00:00:00Z to 2021-01-11T00:00:00Z
        // Week 3 from 2021-01-11T00:00:00Z to 2021-01-18T00:00:00Z
        // Week 1 has 2 focus times of 7 hours in it
        // Week 2 and 3 have 0 focus times in them
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeTotalAmountPartiallyMeetingUserWeeklyTarget)
            .givenSolution(solution)
            .penalizesBy(13 * 60)
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeTotalAmountIsZeroInAWeekForGivenUser)
            .givenSolution(solution)
            .penalizesBy(2 * 20 * 60)
    }

    @Test
    fun `penalize if existing focus time is moved`() {
        val existingFocusTimeThatHasNotMoved = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 12,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent1,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val existingFocusTimeThatHasMoved = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-12T00:00:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = CalendarEventFixtures.focusTimeCalendarEvent2,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val externalOptimizerEvent1 = OptimizerEvent(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-03T00:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = UserFixtures.djei2WithPreferences.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(existingFocusTimeThatHasNotMoved, existingFocusTimeThatHasMoved, externalOptimizerEvent1),
            users = listOf(UserFixtures.djei2WithPreferences)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::existingFocusTimeShouldOnlyBeMovedIfTheyGiveMoreFocusTime)
            .givenSolution(solution)
            .penalizesBy(30)
    }

    @Test
    fun `penalize if focus time is not within preferred focus time range`() {
        // userPreferences is set to preferred focus time range 14:00 - 17:00
        val userPreferences = UserFixtures.userPreferences.copy(
            preferredTimeZone = TimeZone.of("Europe/London"),
            preferredFocusTimeRange = LocalTimeSpan(LocalTime(14, 0), LocalTime(17, 0))
        )
        val user = UserFixtures.djei2WithPreferences.copy(
            preferences = userPreferences
        )
        val focusTimeOutsidePreferredRange = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-24T07:00:00Z")
            ),
            durationInTimeGrains = 12,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val focusTimeWithinPreferredRange = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-25T16:00:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val externalOptimizerEvent1OutsidePreferredRange = OptimizerEvent(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-26T07:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                focusTimeOutsidePreferredRange,
                focusTimeWithinPreferredRange,
                externalOptimizerEvent1OutsidePreferredRange
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeShouldBeWithinPreferredFocusTimeRange)
            .givenSolution(solution)
            .penalizesBy(12 * 15)
    }

    @Test
    fun `penalize if focus time is not scheduled on the hour or half hour`() {
        val userPreferences = UserFixtures.userPreferences.copy(
            preferredTimeZone = TimeZone.of("Europe/London")
        )
        val user = UserFixtures.djei2WithPreferences.copy(
            preferences = userPreferences
        )
        val onTheHour = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-24T07:00:00Z")
            ),
            durationInTimeGrains = 12,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val onTheHalfHour = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-25T16:00:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val at15Minutes = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-26T16:15:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val at45Minutes = OptimizerEvent(
            id = "4",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-27T16:45:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )
        val externalOptimizerEvent = OptimizerEvent(
            id = "5",
            type = CalendarEventType.EXTERNAL_EVENT,
            title = "external event 1",
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-03-26T07:15:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = CalendarEventFixtures.externalTypeCalendarEvent,
            owner = user.email,
            personalTaskId = null,
            personalTaskTargetDurationInMinutes = null,
            isHighPriorityPersonalTask = null
        )

        val solution = OptimizationProblem(
            parameters = OptimizationProblem.OptimizationProblemParameters(
                optimizationReferenceInstant = Instant.parse("2021-01-01T00:00:00Z"),
                optimizationMaxRangeInWeeks = 2,
                weekStartDayOfWeek = DayOfWeek.MONDAY
            ),
            schedule = listOf(
                onTheHour,
                onTheHalfHour,
                at15Minutes,
                at45Minutes,
                externalOptimizerEvent
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimesShouldBeScheduledOnTheHourOrHalfHour)
            .givenSolution(solution)
            .penalizesBy(2)
    }
}
