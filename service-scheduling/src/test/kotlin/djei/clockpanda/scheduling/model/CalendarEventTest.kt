package djei.clockpanda.scheduling.model

import arrow.core.Either
import djei.clockpanda.model.CalendarProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class CalendarEventTest {
    private val nullStartDateTimeCalendarEvent = CalendarEvent(
        id = "id",
        title = "title",
        description = "description",
        calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
        startTime = null,
        endTime = Clock.System.now(),
        startDate = null,
        endDate = null,
        iCalUid = "iCalUid",
        isRecurring = false,
        owner = "owner"
    )

    private val nullEndDateTimeCalendarEvent = CalendarEvent(
        id = "id",
        title = "title",
        description = "description",
        calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
        startTime = Clock.System.now(),
        endTime = null,
        startDate = null,
        endDate = null,
        iCalUid = "iCalUid",
        isRecurring = false,
        owner = "owner"
    )

    @Test
    fun `test getTimeSpan returns left if start date, end date, start time, end time are not properly set`() {
        val result1 = nullStartDateTimeCalendarEvent.getTimeSpan(TimeZone.UTC)
        val result2 = nullEndDateTimeCalendarEvent.getTimeSpan(TimeZone.UTC)

        when (result1) {
            is Either.Left -> {
                assertThat(result1.value).isInstanceOf(CalendarEventError.GetTimeSpanError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
        when (result2) {
            is Either.Left -> {
                assertThat(result2.value).isInstanceOf(CalendarEventError.GetTimeSpanError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
    }

    @Test
    fun `test getDurationInMinutes returns left if start date, end date, start time, end time are not properly set`() {
        val result1 = nullStartDateTimeCalendarEvent.getDurationInMinutes(TimeZone.UTC)
        val result2 = nullEndDateTimeCalendarEvent.getDurationInMinutes(TimeZone.UTC)

        when (result1) {
            is Either.Left -> {
                assertThat(result1.value).isInstanceOf(CalendarEventError.GetTimeSpanError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
        when (result2) {
            is Either.Left -> {
                assertThat(result2.value).isInstanceOf(CalendarEventError.GetTimeSpanError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
    }
}
