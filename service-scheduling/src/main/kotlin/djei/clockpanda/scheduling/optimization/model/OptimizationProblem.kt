package djei.clockpanda.scheduling.optimization.model

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.extensions.getNextDayOfWeek
import djei.clockpanda.scheduling.extensions.getPreviousDayOfWeek
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.OptimizationService
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@NoArg
@PlanningSolution
class OptimizationProblem(
    @ProblemFactProperty
    val parameters: OptimizationProblemParameters,
    @PlanningEntityCollectionProperty
    val schedule: List<OptimizerEvent>,
    @ProblemFactCollectionProperty
    val users: List<User>
) {
    private var score: HardMediumSoftScore = HardMediumSoftScore.ZERO

    @PlanningScore
    fun getPlanningScore(): HardMediumSoftScore {
        return score
    }

    fun setPlanningScore(score: HardMediumSoftScore) {
        this.score = score
    }

    @ValueRangeProvider(id = "startTimeGrainRange")
    fun getStartTimeGrainRange(): List<TimeGrain> {
        val planningEntityOptimizationRange = parameters.planningEntityOptimizationRange
        val result = mutableListOf<TimeGrain>()
        result.add(TimeGrain(planningEntityOptimizationRange.start))
        while (
            result.last().start.plus(
                TimeGrain.TIME_GRAIN_RESOLUTION,
                DateTimeUnit.MINUTE
            ) < planningEntityOptimizationRange.end
        ) {
            result.add(TimeGrain(result.last().start.plus(TimeGrain.TIME_GRAIN_RESOLUTION, DateTimeUnit.MINUTE)))
        }
        return result
    }

    data class OptimizationProblemParameters(
        // We allow passing a reference instant for testing purposes
        // But for production we should default to using current time as reference
        val optimizationReferenceInstant: Instant = Clock.System.now(),
        val optimizationMaxRangeInWeeks: Int,
        val weekStartDayOfWeek: DayOfWeek,
    ) {
        /**
         * The planning entity optimization range is the range of time that planning entities can be moved around in to optimize our schedule
         * We optimize for the following `optimizationMaxRange` calendar weeks including the week of the optimization reference date
         * For example, if the optimization reference date is on Wednesday, we add `optimizationMaxRange` weeks at optimize until the sunday of that week
         */
        val planningEntityOptimizationRange: TimeSpan

        /**
         * The existing schedule consideration range is the range of the time that we need to pull existing events from to
         * properly optimize the desired planning entity optimization range.
         * For example, if the optimization reference date is on Wednesday, we need to pull existing events from the start of week
         */
        val existingScheduleConsiderationRange: TimeSpan

        companion object {
            // Optimization works in UTC timezone: this is by design to avoid any DST issues
            // when working with different users with different preferred time zones
            val OPTIMIZATION_TIMEZONE = TimeZone.UTC
        }

        init {
            val planningEntityOptimizationRangeStartDate = optimizationReferenceInstant
                .toLocalDateTime(OPTIMIZATION_TIMEZONE)
                .date
                // Reference instant is usually current
                // Therefore only start optimizing for the day after tomorrow
                // We do not want to change tomorrow's schedule as this would be bad user experience
                .plus(2, DateTimeUnit.DAY)
            val planningEntityOptimizationRangeEndDate = planningEntityOptimizationRangeStartDate
                .plus(optimizationMaxRangeInWeeks, DateTimeUnit.WEEK)
                .getNextDayOfWeek(weekStartDayOfWeek, inclusive = false)
            planningEntityOptimizationRange = TimeSpan(
                start = planningEntityOptimizationRangeStartDate.atStartOfDayIn(OPTIMIZATION_TIMEZONE),
                end = planningEntityOptimizationRangeEndDate.atStartOfDayIn(OPTIMIZATION_TIMEZONE)
            )
            val existingScheduleRangeStartDate = planningEntityOptimizationRange.start
                .toLocalDateTime(OPTIMIZATION_TIMEZONE)
                .date
                .getPreviousDayOfWeek(OptimizationService.WEEK_START_DAY_OF_WEEK, inclusive = true)
            existingScheduleConsiderationRange = TimeSpan(
                start = existingScheduleRangeStartDate.atStartOfDayIn(OPTIMIZATION_TIMEZONE),
                end = planningEntityOptimizationRange.end
            )
        }

        fun splitExistingScheduleConsiderationRangeInWeeklyBuckets(): List<TimeSpan> {
            val result = mutableListOf<TimeSpan>()
            var currentStartDate = existingScheduleConsiderationRange.start.toLocalDateTime(OPTIMIZATION_TIMEZONE).date
            while (currentStartDate < existingScheduleConsiderationRange.end.toLocalDateTime(OPTIMIZATION_TIMEZONE).date) {
                val currentEndDate = currentStartDate.plus(1, DateTimeUnit.WEEK)
                result.add(
                    TimeSpan(
                        currentStartDate.atStartOfDayIn(OPTIMIZATION_TIMEZONE),
                        currentEndDate.atStartOfDayIn(OPTIMIZATION_TIMEZONE)
                    )
                )
                currentStartDate = currentEndDate
            }
            return result
        }
    }
}
