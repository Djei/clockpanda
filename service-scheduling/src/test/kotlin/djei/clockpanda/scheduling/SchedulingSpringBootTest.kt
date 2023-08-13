package djei.clockpanda.scheduling

import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade
import djei.clockpanda.testing.ClockPandaSpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@ClockPandaSpringBootTest
class SchedulingSpringBootTest {
    @MockBean
    lateinit var googleCalendarApiFacade: GoogleCalendarApiFacade
}
