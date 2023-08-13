package djei.clockpanda.scheduling.optimization

import kotlinx.datetime.Instant

data class TimeGrain(
    val start: Instant
) {
    companion object {
        const val GRAIN_LENGTH_IN_MINUTES = 15
    }
}
