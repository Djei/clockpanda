package djei.clockpanda.scheduling.optimization.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OptimizationProblemTest {

    @Test
    fun `test getStartTimeGrainRange return proper values`() {
        val parameters = OptimizationProblem.OptimizationProblemParameters(
            optimizationReferenceInstant = Instant.parse("2023-08-16T22:00:00Z"),
            optimizationMaxRangeInWeeks = 2,
            weekStartDayOfWeek = DayOfWeek.MONDAY
        )
        val optimizationProblem = OptimizationProblem(
            parameters = parameters,
            schedule = emptyList(),
            users = emptyList()
        )

        val result = optimizationProblem.getStartTimeGrainRange()

        assertThat(parameters.planningEntityOptimizationRange.start).isEqualTo(
            Instant.parse("2023-08-18T00:00:00Z")
        )
        assertThat(parameters.planningEntityOptimizationRange.end).isEqualTo(
            Instant.parse("2023-09-04T00:00:00Z")
        )
        // 17 days in planning entity optimization range
        // 96 15-minutes time grains per day
        assertThat(result.size).isEqualTo(96 * 17)
        // Check that all time grains are at the 15 minutes mark
        assertThat(result.all { it.start.toLocalDateTime(TimeZone.UTC).minute in listOf(0, 15, 30, 45) })
        // Check that all time grains are different
        assertThat(result.distinct().size).isEqualTo(result.size)
    }

    @Test
    fun `test OptimizationProblemParameters constructor`() {
        val optimizationExecutedOnWednesdayParameters = OptimizationProblem.OptimizationProblemParameters(
            optimizationReferenceInstant = Instant.parse("2023-08-16T22:00:00Z"),
            optimizationMaxRangeInWeeks = 2,
            weekStartDayOfWeek = DayOfWeek.MONDAY
        )
        val optimizationExecutedOnSundayParameters = OptimizationProblem.OptimizationProblemParameters(
            optimizationReferenceInstant = Instant.parse("2023-08-13T22:00:00Z"),
            optimizationMaxRangeInWeeks = 2,
            weekStartDayOfWeek = DayOfWeek.MONDAY
        )

        assertThat(optimizationExecutedOnWednesdayParameters.planningEntityOptimizationRange.start).isEqualTo(
            Instant.parse("2023-08-18T00:00:00Z")
        )
        assertThat(optimizationExecutedOnWednesdayParameters.planningEntityOptimizationRange.end).isEqualTo(
            Instant.parse("2023-09-04T00:00:00Z")
        )
        assertThat(optimizationExecutedOnWednesdayParameters.existingScheduleConsiderationRange.start).isEqualTo(
            Instant.parse("2023-08-14T00:00:00Z")
        )
        assertThat(optimizationExecutedOnWednesdayParameters.existingScheduleConsiderationRange.end).isEqualTo(
            Instant.parse("2023-09-04T00:00:00Z")
        )
        assertThat(optimizationExecutedOnSundayParameters.planningEntityOptimizationRange.start).isEqualTo(
            Instant.parse("2023-08-15T00:00:00Z")
        )
        assertThat(optimizationExecutedOnSundayParameters.planningEntityOptimizationRange.end).isEqualTo(
            Instant.parse("2023-09-04T00:00:00Z")
        )
        assertThat(optimizationExecutedOnSundayParameters.existingScheduleConsiderationRange.start).isEqualTo(
            Instant.parse("2023-08-14T00:00:00Z")
        )
        assertThat(optimizationExecutedOnSundayParameters.existingScheduleConsiderationRange.end).isEqualTo(
            Instant.parse("2023-09-04T00:00:00Z")
        )
    }

    @Test
    fun `test OptimizationProblemParameters splitExistingScheduleConsiderationRangeInWeeklyBuckets`() {
        val optimizationExecutedOnWednesdayParameters = OptimizationProblem.OptimizationProblemParameters(
            optimizationReferenceInstant = Instant.parse("2023-08-16T22:00:00Z"),
            optimizationMaxRangeInWeeks = 2,
            weekStartDayOfWeek = DayOfWeek.MONDAY
        )

        val result = optimizationExecutedOnWednesdayParameters.splitExistingScheduleConsiderationRangeInWeeklyBuckets()

        assertThat(result).hasSize(3)
        assertThat(result[0].start).isEqualTo(Instant.parse("2023-08-14T00:00:00Z"))
        assertThat(result[0].end).isEqualTo(Instant.parse("2023-08-21T00:00:00Z"))
        assertThat(result[1].start).isEqualTo(Instant.parse("2023-08-21T00:00:00Z"))
        assertThat(result[1].end).isEqualTo(Instant.parse("2023-08-28T00:00:00Z"))
        assertThat(result[2].start).isEqualTo(Instant.parse("2023-08-28T00:00:00Z"))
        assertThat(result[2].end).isEqualTo(Instant.parse("2023-09-04T00:00:00Z"))
    }
}
