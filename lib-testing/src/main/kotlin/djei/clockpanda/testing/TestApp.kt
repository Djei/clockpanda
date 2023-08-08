package djei.clockpanda.testing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication(
    scanBasePackages = ["djei.clockpanda"]
)
@ConfigurationPropertiesScan(basePackages = ["djei.clockpanda"])
class TestApp
