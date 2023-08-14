package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

class OptimizationConstraintsProviderTest {
    private val constraintVerifier = ConstraintVerifier.build(
        OptimizationConstraintsProvider(),
        OptimizationProblem::class.java,
        Event::class.java
    )

    @Test
    fun `penalize if focus time event overlaps with other events`() {
        val focusTime1 = Event(
            id = "focus-time-1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T02:00:00Z
            owner = UserFixtures.userWithPreferences.email
        )
        val focusTime2 = Event(
            id = "focus-time-2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:45:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T03:45:00Z
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 1 partially overlaps with focus time
        val externalEvent1 = Event(
            id = "6fpknj2tjkc2ee2v32q7ve8t04",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T03:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T05:00:00Z
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 2 does not overlap with focus time but with external event 1 (which should not be penalized)
        val externalEvent2 = Event(
            id = "bpokgerzlkc2ee2v32asdlklw",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T04:45:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T06:45:00Z
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 3 is completely contained within focus time
        val externalEvent3 = Event(
            id = "asdqwejkasdjo9i3jodas236",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T02:00:00Z")
            ),
            durationInTimeGrains = 4, // End time: 2021-01-01T03:00:00Z
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 4 completely contains focus time
        val externalEvent4 = Event(
            id = "asdjoilkjio3940dsfoj312",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:30:00Z")
            ),
            durationInTimeGrains = 24, // End time: 2021-01-01T07:30:00Z
            owner = UserFixtures.userWithPreferences.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(
                focusTime1,
                focusTime2,
                externalEvent1,
                externalEvent2,
                externalEvent3,
                externalEvent4
            ),
            users = listOf(UserFixtures.userWithPreferences)
        )

        // Following overlaps are penalized:
        // - focusTime1 partially overlaps with focusTime2 - 15 mins
        // - externalEvent1 partially overlaps with focusTime2 - 45 mins
        // - externalEvent3 is fully contained within focusTime2 - 60 mins
        // - externalEvent4 fully contains focusTime2 - 120 mins
        // - externalEvent4 partially overlaps with focusTime1 - 30 mins
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeEventsShouldNotOverlapWithOtherEvents)
            .givenSolution(solution)
            .penalizesBy(270)
    }

    @Test
    fun `penalize if focus time is outside of user preferences working hours`() {
        val focusTimeOutsideWorkingHours = Event(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T08:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )
        val focusTimeInsideWorkingHours = Event(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T14:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(focusTimeOutsideWorkingHours, focusTimeInsideWorkingHours),
            users = listOf(UserFixtures.userWithPreferences)
        )

        // Focus time 1 is outside of working hours and penalized for 60 minutes
        // Focus time 2 is inside of working hours and not penalized
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeEventsShouldNotBeOutsideOfWorkingHours)
            .givenSolution(solution)
            .penalizesBy(60)
    }

    @Test
    fun `penalize if focus time start and end are not on the same day (in user preferred timezone)`() {
        val userPreferenceWithEuropeLondonPreferredTimezone = UserFixtures.userPreferences.copy(
            preferredTimeZone = TimeZone.of("Europe/London")
        )
        val user = UserFixtures.userWithPreferences.copy(
            preferences = userPreferenceWithEuropeLondonPreferredTimezone
        )
        val focusTimeWithStartAndEndDateNotSameDay = Event(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T23:00:00Z")
            ),
            durationInTimeGrains = 24,
            owner = user.email
        )
        val focusTimeNotPassingInUserTimeZone = Event(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-07-07T23:45:00Z")
            ),
            durationInTimeGrains = 2,
            owner = user.email
        )
        val externalEventDoesNotCount = Event(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T23:00:00Z")
            ),
            durationInTimeGrains = 24,
            owner = user.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(
                focusTimeWithStartAndEndDateNotSameDay,
                focusTimeNotPassingInUserTimeZone,
                externalEventDoesNotCount
            ),
            users = listOf(user)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeShouldStartAndEndOnTheSameDay)
            .givenSolution(solution)
            .penalizesBy(1)
    }

    @Test
    fun `penalize if total focus time does not meet user target`() {
        val threeHoursOfFocusTime = Event(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 12,
            owner = UserFixtures.userWithPreferences.email
        )
        val fourHoursOfFocusTime = Event(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-02T00:00:00Z")
            ),
            durationInTimeGrains = 16,
            owner = UserFixtures.userWithPreferences.email
        )
        val externalEvent1 = Event(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-03T00:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(threeHoursOfFocusTime, fourHoursOfFocusTime, externalEvent1),
            users = listOf(UserFixtures.userWithPreferences)
        )

        // User preference wants 20 hours of focus time per week, but only 7 hours are scheduled
        // 33 hours missing equivalent to 132 15-minute blocks
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeTotalAmountNotMeetingTheTarget)
            .givenSolution(solution)
            .penalizesBy(132)
    }
}
