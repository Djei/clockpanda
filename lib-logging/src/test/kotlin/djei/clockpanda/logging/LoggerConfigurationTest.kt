package djei.clockpanda.logging

import djei.clockpanda.testing.ClockPandaSpringBootTest
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@ClockPandaSpringBootTest
class LoggerConfigurationTest {
    @Autowired
    private lateinit var testClass: TestClass

    @Test
    fun `test logger configuration injects logger in testClass`() {
        testClass.log()
    }
}

@Component
private class TestClass(
    private val logger: Logger
) {
    fun log() {
        logger.info("test")
    }
}
