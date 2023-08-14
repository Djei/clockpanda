package djei.clockpanda.scheduling.optimization

import arrow.core.left
import arrow.core.right
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.SchedulingSpringBootTest
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacadeError
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.scheduling.optimization.OptimizationService.Companion.OPTIMIZATION_RANGE_IN_DAYS
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atMost
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.willReturn
import org.springframework.beans.factory.annotation.Autowired

class OptimizationCronJobTest : SchedulingSpringBootTest() {

    @Autowired
    lateinit var optimizationCronJob: OptimizationCronJob

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var dslContext: DSLContext

    @Test
    fun `test run optimization cron job failure does not throw exception`() {
        userRepository.create(dslContext, UserFixtures.userWithPreferences)
        given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
            GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError(RuntimeException("some error")).left()
        )

        optimizationCronJob.runOptimization()

        verify(googleCalendarApiFacade, never()).deleteCalendarEvent(any(), any())
        verify(googleCalendarApiFacade, never()).createCalendarEvent(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `test run optimization cron job - failure to clean existing focus times`() {
        userRepository.create(dslContext, UserFixtures.userWithPreferences)
        given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
            listOf(
                CalendarEventFixtures.focusTimeCalendarEvent1,
                CalendarEventFixtures.externalTypeCalendarEvent
            ).right()
        )
        given(googleCalendarApiFacade.deleteCalendarEvent(any(), any())).willReturn {
            GoogleCalendarApiFacadeError.GoogleCalendarApiDeleteEventError(RuntimeException("some error")).left()
        }

        optimizationCronJob.runOptimization()

        verify(googleCalendarApiFacade).deleteCalendarEvent(
            eq(UserFixtures.userWithPreferences),
            eq(CalendarEventFixtures.focusTimeCalendarEvent1)
        )
        verify(googleCalendarApiFacade, never()).createCalendarEvent(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `test run optimization cron job - failure to create new focus times`() {
        userRepository.create(dslContext, UserFixtures.userWithPreferences)
        given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
            listOf(
                CalendarEventFixtures.focusTimeCalendarEvent1,
                CalendarEventFixtures.focusTimeCalendarEvent2,
                CalendarEventFixtures.externalTypeCalendarEvent
            ).right()
        )
        given(googleCalendarApiFacade.deleteCalendarEvent(any(), any())).willReturn {
            Unit.right()
        }
        given(googleCalendarApiFacade.createCalendarEvent(any(), any(), anyOrNull(), any(), any())).willReturn {
            GoogleCalendarApiFacadeError.GoogleCalendarApiCreateEventError(RuntimeException("some error")).left()
        }

        optimizationCronJob.runOptimization()

        verify(googleCalendarApiFacade).deleteCalendarEvent(
            eq(UserFixtures.userWithPreferences),
            eq(CalendarEventFixtures.focusTimeCalendarEvent1)
        )
        verify(googleCalendarApiFacade).deleteCalendarEvent(
            eq(UserFixtures.userWithPreferences),
            eq(CalendarEventFixtures.focusTimeCalendarEvent2)
        )
        verify(googleCalendarApiFacade).createCalendarEvent(
            any(),
            eq(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE),
            anyOrNull(),
            any(),
            any()
        )
    }

    @Test
    fun `test run optimization cron job - happy path`() {
        userRepository.create(dslContext, UserFixtures.userWithPreferences)
        given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
            listOf(
                CalendarEventFixtures.focusTimeCalendarEvent1,
                CalendarEventFixtures.focusTimeCalendarEvent2,
                CalendarEventFixtures.externalTypeCalendarEvent
            ).right()
        )
        given(googleCalendarApiFacade.deleteCalendarEvent(any(), any())).willReturn {
            Unit.right()
        }
        given(googleCalendarApiFacade.createCalendarEvent(any(), any(), anyOrNull(), any(), any()))
            .willReturn {
                CalendarEventFixtures.focusTimeCalendarEvent1.right()
            }

        optimizationCronJob.runOptimization()

        verify(googleCalendarApiFacade).deleteCalendarEvent(
            eq(UserFixtures.userWithPreferences),
            eq(CalendarEventFixtures.focusTimeCalendarEvent1)
        )
        verify(googleCalendarApiFacade).deleteCalendarEvent(
            eq(UserFixtures.userWithPreferences),
            eq(CalendarEventFixtures.focusTimeCalendarEvent2)
        )
        verify(googleCalendarApiFacade, never()).deleteCalendarEvent(
            any(),
            eq(CalendarEventFixtures.externalTypeCalendarEvent)
        )
        verify(googleCalendarApiFacade, atMost(OPTIMIZATION_RANGE_IN_DAYS)).createCalendarEvent(
            any(),
            eq(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE),
            anyOrNull(),
            any(),
            any()
        )
    }
}
