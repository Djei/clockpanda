package djei.clockpanda.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

typealias WorkingHours = Map<DayOfWeek, List<WorkingHourBlock>>

@Serializable
data class WorkingHourBlock(
    val start: LocalTime,
    val end: LocalTime
)
