package djei.clockpanda.scheduling.extensions

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Returns the next day of the week after this date.
 * If this instance is already on the given day of the week, inclusive parameter determines whether it is returned or the next one is
 */
fun LocalDate.getNextDayOfWeek(dayOfWeek: DayOfWeek, inclusive: Boolean): LocalDate {
    var result = if (inclusive) {
        this
    } else {
        this.plus(1, DateTimeUnit.DAY)
    }
    while (result.dayOfWeek != dayOfWeek) {
        result = result.plus(1, DateTimeUnit.DAY)
    }
    return result
}

/**
 * Returns the previous day of the week before this date.
 * If this instance is already on the given day of the week, inclusive parameter determines whether it is returned or the next one is
 */
fun LocalDate.getPreviousDayOfWeek(dayOfWeek: DayOfWeek, inclusive: Boolean): LocalDate {
    var result = if (inclusive) {
        this
    } else {
        this.minus(1, DateTimeUnit.DAY)
    }
    while (result.dayOfWeek != dayOfWeek) {
        result = result.minus(1, DateTimeUnit.DAY)
    }
    return result
}
