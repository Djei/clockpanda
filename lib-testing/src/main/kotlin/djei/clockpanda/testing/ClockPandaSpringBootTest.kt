package djei.clockpanda.testing

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [TestApp::class])
@ActiveProfiles("test")
@AutoConfigureMockMvc
annotation class ClockPandaSpringBootTest
