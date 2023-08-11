package djei.clockpanda.scheduling.model

import kotlinx.datetime.Instant

data class TimeSpan(
    val start: Instant,
    val end: Instant
)
