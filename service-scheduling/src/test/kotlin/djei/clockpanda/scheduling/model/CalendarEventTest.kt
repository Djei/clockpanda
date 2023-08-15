package djei.clockpanda.scheduling.model

import arrow.core.Either
import djei.clockpanda.model.CalendarProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class CalendarEventTest {

    private val goodStartEndDateCalendarEvent = CalendarEvent(
        id = "id",
        title = "title",
        description = "description",
        calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
        startTime = null,
        endTime = null,
        startDate = LocalDate(2021, 1, 1),
        endDate = LocalDate(2021, 1, 2),
        iCalUid = "iCalUid",
        isRecurring = false,
        owner = "owner"
    )

    private val goodStartEndTimeCalendarEvent = CalendarEvent(
        id = "id",
        title = "title",
        description = "description",
        calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
        startTime = Instant.parse("2021-01-01T00:00:00Z"),
        endTime = Instant.parse("2021-01-01T01:00:00Z"),
        startDate = null,
        endDate = null,
        iCalUid = "iCalUid",
        isRecurring = false,
        owner = "owner"
    )

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
    fun `test toGoogleCalendarEventForUpdate - happy path`() {
        val result1 = goodStartEndDateCalendarEvent.toGoogleCalendarEventForUpdate()
        val result2 = goodStartEndTimeCalendarEvent.toGoogleCalendarEventForUpdate()

        when (result1) {
            is Either.Left -> fail("This should return right value", result1.value)
            is Either.Right -> {
                val eventToInsert = result1.value
                assertThat(eventToInsert.id).isEqualTo(goodStartEndDateCalendarEvent.id)
                assertThat(eventToInsert.summary).isEqualTo(goodStartEndDateCalendarEvent.title)
                assertThat(eventToInsert.description).isEqualTo(goodStartEndDateCalendarEvent.description)
                assertThat(eventToInsert.start.date.toStringRfc3339()).isEqualTo("2021-01-01")
                assertThat(eventToInsert.end.date.toStringRfc3339()).isEqualTo("2021-01-02")
            }
        }
        when (result2) {
            is Either.Left -> fail("This should return right value", result2.value)
            is Either.Right -> {
                val eventToInsert = result2.value
                assertThat(eventToInsert.id).isEqualTo(goodStartEndTimeCalendarEvent.id)
                assertThat(eventToInsert.summary).isEqualTo(goodStartEndDateCalendarEvent.title)
                assertThat(eventToInsert.description).isEqualTo(goodStartEndDateCalendarEvent.description)
                assertThat(eventToInsert.start.dateTime.toStringRfc3339()).isEqualTo("2021-01-01T00:00:00.000Z")
                assertThat(eventToInsert.end.dateTime.toStringRfc3339()).isEqualTo("2021-01-01T01:00:00.000Z")
            }
        }
    }

    @Test
    fun `test toGoogleCalendarEventForUpdate returns left if start date, end date, start time, end time are not properly set`() {
        val result1 = nullStartDateTimeCalendarEvent.toGoogleCalendarEventForUpdate()
        val result2 = nullEndDateTimeCalendarEvent.toGoogleCalendarEventForUpdate()

        when (result1) {
            is Either.Left -> {
                assertThat(result1.value).isInstanceOf(CalendarEventError.ToGoogleCalendarEventForUpdateError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
        when (result2) {
            is Either.Left -> {
                assertThat(result2.value).isInstanceOf(CalendarEventError.ToGoogleCalendarEventForUpdateError::class.java)
            }

            is Either.Right -> fail("This should return left value")
        }
    }

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
