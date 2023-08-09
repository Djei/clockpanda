package djei.clockpanda.testing

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [TestApp::class])
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
annotation class ClockPandaSpringBootTest
