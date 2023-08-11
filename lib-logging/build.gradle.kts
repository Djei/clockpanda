plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    testImplementation(project(":lib-testing"))
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            // Disable all default coverage rules from backend convention to define our own
            rules.forEach { it.isEnabled = false }

            // lib-logging contains only Spring configuration code, which is harder to cover with unit tests
            // Hence we set a lower coverage threshold for them
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = BigDecimal(0.8)
                }
            }

            rule {
                limit {
                    counter = "BRANCH"
                    minimum = BigDecimal(0.3)
                }
            }
        }
    }
}
