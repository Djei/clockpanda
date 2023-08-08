package djei.clockpanda.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["djei.clockpanda"]
)
@ConfigurationPropertiesScan(basePackages = ["djei.clockpanda"])
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}