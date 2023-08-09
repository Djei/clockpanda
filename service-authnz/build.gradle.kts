plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    implementation(project(":lib-database"))

    implementation("org.jooq:jooq")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    testImplementation(project(":lib-testing"))
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            // Disable all default coverage rules from backend convention to define our own
            rules.forEach { it.isEnabled = false }

            // The `service-authnz` module contains a lot of Spring Security configuration boilerplate code
            // They are harder to cover with unit tests, so we set a lower coverage threshold for them
            // This is not ideal but mitigated by the fact that those should not often change once written
            // and have been validated by running the application locally
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = BigDecimal(0.30)
                }
            }

            rule {
                limit {
                    counter = "BRANCH"
                    minimum = BigDecimal(0.01)
                }
            }
        }
    }
}
