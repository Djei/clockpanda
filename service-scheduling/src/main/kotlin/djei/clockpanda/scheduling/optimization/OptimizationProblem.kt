package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@NoArg
@PlanningSolution
class OptimizationProblem(
    val optimizationRange: TimeSpan,
    @PlanningEntityCollectionProperty
    val schedule: List<Event>,
    @ProblemFactCollectionProperty
    val users: List<User>
) {
    private var score: HardMediumSoftScore = HardMediumSoftScore.ZERO

    init {
        require(optimizationRange.start < optimizationRange.end) {
            "Optimization range start must be before end"
        }
        require(
            optimizationRange.start.toLocalDateTime(TimeZone.UTC).hour == 0 &&
                optimizationRange.start.toLocalDateTime(TimeZone.UTC).minute == 0
        ) {
            "Optimization range start must be at start of day in UTC"
        }
        require(
            optimizationRange.end.toLocalDateTime(TimeZone.UTC).hour == 0 &&
                optimizationRange.end.toLocalDateTime(TimeZone.UTC).minute == 0
        ) {
            "Optimization range end must be at start of day in UTC"
        }
    }

    @PlanningScore
    fun getPlanningScore(): HardMediumSoftScore {
        return score
    }

    fun setPlanningScore(score: HardMediumSoftScore) {
        this.score = score
    }

    @ValueRangeProvider(id = "startTimeGrainRange")
    fun getStartTimeGrainRange(): List<TimeGrain> {
        val result = mutableListOf<TimeGrain>()
        result.add(TimeGrain(optimizationRange.start))
        while (
            result.last().start.plus(TimeGrain.GRAIN_LENGTH_IN_MINUTES, DateTimeUnit.MINUTE) < optimizationRange.end
        ) {
            result.add(TimeGrain(result.last().start.plus(TimeGrain.GRAIN_LENGTH_IN_MINUTES, DateTimeUnit.MINUTE)))
        }
        return result
    }
}
