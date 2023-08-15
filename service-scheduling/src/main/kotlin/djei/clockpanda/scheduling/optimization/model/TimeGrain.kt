package djei.clockpanda.scheduling.optimization.model

import kotlinx.datetime.Instant

data class TimeGrain(
    val start: Instant
) {
    companion object {
        // The time grain resolution is the smallest resolution of time that we work in
        const val TIME_GRAIN_RESOLUTION = 15
    }
}
