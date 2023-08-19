package djei.clockpanda.scheduling.optimization.model

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import djei.clockpanda.scheduling.model.CalendarEvent
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.DurationUnit

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
    val originalCalendarEvent: CalendarEvent?,
    val owner: String
) {
    companion object {
        fun fromCalendarEvent(calendarEvent: CalendarEvent, timeZone: TimeZone): Event {
            val calendarEventTimeSpan = calendarEvent.getTimeSpan(timeZone)
            val calendarEventDurationInMinutes = calendarEvent.getDurationInMinutes(timeZone)
            return Event(
                id = calendarEvent.id,
                startTimeGrain = TimeGrain(calendarEventTimeSpan.start),
                durationInTimeGrains = calendarEventDurationInMinutes / TimeGrain.TIME_GRAIN_RESOLUTION,
                type = calendarEvent.getCalendarEventType(),
                originalCalendarEvent = calendarEvent,
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
            (durationInTimeGrains ?: 0) * TimeGrain.TIME_GRAIN_RESOLUTION,
            DateTimeUnit.MINUTE
        )
    }

    fun hasChangedFromOriginal(timeZone: TimeZone): Boolean {
        return if (originalCalendarEvent == null) {
            false
        } else {
            val calendarEventTimeSpan = originalCalendarEvent.getTimeSpan(timeZone)
            calendarEventTimeSpan.start != getStartTime() || calendarEventTimeSpan.end != getEndTime()
        }
    }

    fun computeOverlapInMinutes(other: Event): Int {
        val overlapStart = maxOf(getStartTime(), other.getStartTime())
        val overlapEnd = minOf(getEndTime(), other.getEndTime())
        return if (overlapStart < overlapEnd) {
            (overlapEnd - overlapStart).toInt(DurationUnit.MINUTES)
        } else {
            0
        }
    }

    fun computeOutsideRangeInMinutes(range: TimeSpan): Int {
        var amountOfMinutesOutside = 0
        if (getStartTime() < range.start) {
            amountOfMinutesOutside += (minOf(getEndTime(), range.start) - getStartTime()).toInt(DurationUnit.MINUTES)
        }
        if (getEndTime() > range.end) {
            amountOfMinutesOutside += (getEndTime() - maxOf(getStartTime(), range.end)).toInt(DurationUnit.MINUTES)
        }
        return amountOfMinutesOutside
    }

    fun getDurationInMinutes(): Int {
        return (durationInTimeGrains ?: 0) * TimeGrain.TIME_GRAIN_RESOLUTION
    }

    @ValueRangeProvider(id = "durationInTimeGrainsRange")
    fun getDurationInTimeGrainsRange(): List<Int> {
        return listOf(0) + when (type) {
            // Focus time are minimum 2 hours up to 8 hours
            CalendarEventType.FOCUS_TIME -> (8..24).map { it }
            else -> throw IllegalStateException("Duration in time grains range not defined for event type $type")
        }
    }

    override fun toString(): String {
        return "Event(id=$id, startTime=${getStartTime()}, endTime=${getEndTime()}, type=$type, owner=$owner)"
    }
}
