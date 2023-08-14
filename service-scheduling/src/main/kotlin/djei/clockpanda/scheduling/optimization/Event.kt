package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
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
        fun fromCalendarEvent(calendarEvent: CalendarEvent, timeZone: TimeZone): Either<EventError, Event> {
            val calendarEventTimeSpan = calendarEvent.getTimeSpan(timeZone)
                .getOrElse { return EventError.FromCalendarEventError(it).left() }
            val calendarEventDurationInMinutes = calendarEvent.getDurationInMinutes(timeZone)
                .getOrElse { return EventError.FromCalendarEventError(it).left() }
            return Event(
                id = calendarEvent.id,
                startTimeGrain = TimeGrain(calendarEventTimeSpan.start),
                durationInTimeGrains = calendarEventDurationInMinutes / TimeGrain.GRAIN_LENGTH_IN_MINUTES,
                type = calendarEvent.getType(),
                owner = calendarEvent.owner
            ).right()
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

    override fun toString(): String {
        return "Event(id=$id, startTime=${getStartTime()}, endTime=${getEndTime()}, type=$type, owner=$owner)"
    }
}
