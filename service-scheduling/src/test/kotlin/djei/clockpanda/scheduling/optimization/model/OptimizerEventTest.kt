package djei.clockpanda.scheduling.optimization.model

import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OptimizerEventTest {

    @Test
    fun `test compute event overlap in minutes`() {
        // 2 hours reference event
        val referenceOptimizerEvent = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T12:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T14:00:00Z
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )

        // not overlapping before
        val notOverlappingBefore = OptimizerEvent(
            id = "2",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T08:00:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(notOverlappingBefore)).isEqualTo(0)

        // partially overlapping before
        val partiallyOverlappingBefore = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T11:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(partiallyOverlappingBefore)).isEqualTo(60)

        // partially overlapping after
        val partiallyOverlappingAfter = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T13:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(partiallyOverlappingAfter)).isEqualTo(60)

        // not overlapping after
        val notOverlappingAfter = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T15:00:00Z")
            ),
            durationInTimeGrains = 8,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(notOverlappingAfter)).isEqualTo(0)

        // fully contains reference
        val fullyContainsReference = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T11:00:00Z")
            ),
            durationInTimeGrains = 16,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(fullyContainsReference)).isEqualTo(120)

        // fully contained by reference
        val fullyContainedByReference = OptimizerEvent(
            id = "3",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T12:30:00Z")
            ),
            durationInTimeGrains = 4,
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )
        assertThat(referenceOptimizerEvent.computeOverlapInMinutes(fullyContainedByReference)).isEqualTo(60)
    }

    @Test
    fun `test compute outside range in minutes`() {
        // 2 hour reference event
        val referenceOptimizerEvent = OptimizerEvent(
            id = "1",
            type = CalendarEventType.FOCUS_TIME,
            title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
            startTimeGrain = TimeGrain(
                start = Instant.parse("2021-01-01T12:00:00Z")
            ),
            durationInTimeGrains = 8, // End time: 2021-01-01T14:00:00Z
            originalCalendarEvent = null,
            owner = UserFixtures.djei2WithPreferences.email,
            userPersonalTask = null
        )

        // not overlapping before
        val notOverlappingBefore = TimeSpan(
            start = Instant.parse("2021-01-01T08:00:00Z"),
            end = Instant.parse("2021-01-01T10:00:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(notOverlappingBefore)).isEqualTo(120)

        // partially overlapping before
        val partiallyOverlappingBefore = TimeSpan(
            start = Instant.parse("2021-01-01T10:00:00Z"),
            end = Instant.parse("2021-01-01T13:00:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(partiallyOverlappingBefore)).isEqualTo(60)

        // partially overlapping after
        val partiallyOverlappingAfter = TimeSpan(
            start = Instant.parse("2021-01-01T13:00:00Z"),
            end = Instant.parse("2021-01-01T16:00:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(partiallyOverlappingAfter)).isEqualTo(60)

        // not overlapping after
        val notOverlappingAfter = TimeSpan(
            start = Instant.parse("2021-01-01T15:00:00Z"),
            end = Instant.parse("2021-01-01T17:00:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(notOverlappingAfter)).isEqualTo(120)

        // fully contains reference
        val fullyContainsReference = TimeSpan(
            start = Instant.parse("2021-01-01T10:00:00Z"),
            end = Instant.parse("2021-01-01T16:00:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(fullyContainsReference)).isEqualTo(0)

        // fully contained by reference
        val fullyContainedByReference = TimeSpan(
            start = Instant.parse("2021-01-01T12:30:00Z"),
            end = Instant.parse("2021-01-01T13:30:00Z")
        )
        assertThat(referenceOptimizerEvent.computeOutsideRangeInMinutes(fullyContainedByReference)).isEqualTo(60)
    }
}
