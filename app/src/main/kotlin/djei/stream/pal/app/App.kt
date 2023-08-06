package djei.stream.pal.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["djei.stream.pal"]
)
@ConfigurationPropertiesScan(basePackages = ["djei.stream.pal"])
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}
