package djei.clockpanda.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class LocalTimeSpan(
    val start: LocalTime,
    val end: LocalTime
)
