package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.Instant
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
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )
        val focusTime2 = Event(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:45:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 1 partially overlaps with focus time
        val externalEvent1 = Event(
            id = "3",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T03:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 2 does not overlap with focus time but with external event 1 (which should not be penalized)
        val externalEvent2 = Event(
            id = "4",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T04:45:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 3 is completely contained within focus time
        val externalEvent3 = Event(
            id = "5",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T02:00:00Z")
            ),
            durationInTimeGrains = 4,
            owner = UserFixtures.userWithPreferences.email
        )
        // External event 4 completely contains focus time
        val externalEvent4 = Event(
            id = "6",
            type = CalendarEventType.EXTERNAL_EVENT,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T01:30:00Z")
            ),
            durationInTimeGrains = 24,
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
        // - focusTime1 partially overlaps with focusTime2
        // - externalEvent1 partially overlaps with focusTime2
        // - externalEvent3 is fully contained within focusTime2
        // - externalEvent4 fully contains focusTime2
        // - externalEvent4 partially overlaps with focusTime1
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeEventsShouldNotOverlapWithOtherEvents)
            .givenSolution(solution)
            .penalizesBy(5000)
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

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeEventsOutsideOfWorkingHours)
            .givenSolution(solution)
            .penalizesBy(1000)
    }

    @Test
    fun `if user preferences is null, consider everything is valid working hours and solver never penalizes`() {
        val focusTimeOutsideWorkingHours = Event(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T08:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithNoPreferences.email
        )
        val focusTimeInsideWorkingHours = Event(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T14:00:00Z")
            ),
            durationInTimeGrains = 8,
            owner = UserFixtures.userWithNoPreferences.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(focusTimeOutsideWorkingHours, focusTimeInsideWorkingHours),
            users = listOf(UserFixtures.userWithNoPreferences)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeEventsOutsideOfWorkingHours)
            .givenSolution(solution)
            .penalizesBy(0)
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

        // User preference wants 20 hours of focus time, but only 7 hours are scheduled
        // 13 hours missing equivalent to 52 15-minute blocks
        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeTotalAmountNotMeetingTheTarget)
            .givenSolution(solution)
            .penalizesBy(52)
    }

    @Test
    fun `if user preferences is null, target focus time is 0 and solver never penalizes`() {
        val threeHoursOfFocusTime = Event(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T00:00:00Z")
            ),
            durationInTimeGrains = 12,
            owner = UserFixtures.userWithNoPreferences.email
        )

        val solution = OptimizationProblem(
            optimizationRange = TimeSpan(
                start = Instant.parse("2021-01-01T00:00:00Z"),
                end = Instant.parse("2021-01-15T00:00:00Z")
            ),
            schedule = listOf(threeHoursOfFocusTime),
            users = listOf(UserFixtures.userWithNoPreferences)
        )

        constraintVerifier.verifyThat(OptimizationConstraintsProvider::focusTimeTotalAmountNotMeetingTheTarget)
            .givenSolution(solution)
            .penalizesBy(0)
    }
}
