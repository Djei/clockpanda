package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import djei.clockpanda.scheduling.model.CalendarEvent
import djei.clockpanda.scheduling.model.CalendarEventType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

@NoArg
@PlanningEntity
class Event(
    @PlanningId
    val id: String,
    @PlanningVariable(valueRangeProviderRefs = ["startTimeGrainRange"])
    var startTimeGrain: TimeGrain,
    @PlanningVariable(valueRangeProviderRefs = ["durationInTimeGrainsRange"])
    var durationInTimeGrains: Int?,
    val type: CalendarEventType,
    val owner: String
) {
    companion object {
        fun fromCalendarEvent(calendarEvent: CalendarEvent, timeZone: TimeZone): Event {
            return Event(
                id = calendarEvent.id,
                startTimeGrain = TimeGrain(calendarEvent.getTimeSpan(timeZone).start),
                durationInTimeGrains = calendarEvent.getDurationInMinutes(timeZone) / TimeGrain.GRAIN_LENGTH_IN_MINUTES,
                type = calendarEvent.getType(),
                owner = calendarEvent.owner
            )
        }
    }

    @PlanningPin
    fun isPinned(): Boolean {
        return type == CalendarEventType.EXTERNAL_EVENT
    }

    fun getStartTime(): Instant {
        return startTimeGrain.start
    }

    fun getEndTime(): Instant {
        return startTimeGrain.start.plus(
            (durationInTimeGrains ?: 0) * TimeGrain.GRAIN_LENGTH_IN_MINUTES,
            DateTimeUnit.MINUTE
        )
    }

    fun getDurationInMinutes(): Int {
        return (durationInTimeGrains ?: 0) * TimeGrain.GRAIN_LENGTH_IN_MINUTES
    }

    @ValueRangeProvider(id = "durationInTimeGrainsRange")
    fun getDurationInTimeGrainsRange(): List<Int> {
        return listOf(0) + when (type) {
            CalendarEventType.FOCUS_TIME -> (8..24).map { it }
            else -> throw IllegalStateException("Duration in time grains range not defined for event type $type")
        }
    }
}
