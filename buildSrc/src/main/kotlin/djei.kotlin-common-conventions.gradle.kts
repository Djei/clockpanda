plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

val ktlint: Configuration by configurations.creating {
    extendsFrom(configurations["implementation"])
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // ktlint
    ktlint("com.pinterest:ktlint:0.45.0") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // AssertJ for more fluent assertions
    testImplementation("org.assertj:assertj-core:3.23.1")
    // Mockito for mocking in tests
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

tasks {
    compileKotlin {
        kotlinOptions {
            allWarningsAsErrors = true
        }
    }

    compileTestKotlin {
        kotlinOptions {
            allWarningsAsErrors = true
        }
    }

    test {
        useJUnitPlatform()
    }

    withType<JacocoReport> {
        dependsOn(test)
        afterEvaluate {
            classDirectories.setFrom(
                files(
                    classDirectories.files.map {
                        fileTree(it).apply {
                            exclude("djei/clockpanda/jooq/**")
                        }
                    }
                )
            )
        }
    }
    jacocoTestCoverageVerification {
        dependsOn(test)
        mustRunAfter(jacocoTestReport)
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = BigDecimal(0.8)
                }
            }

            rule {
                limit {
                    counter = "BRANCH"
                    minimum = BigDecimal(0.75)
                }
            }
        }
    }
    check {
        dependsOn(jacocoTestReport, jacocoTestCoverageVerification)
    }
}

val outputDir = "${project.buildDir}/reports/ktlint/"
val inputFiles: ConfigurableFileTree = project.fileTree(mapOf("dir" to "src", "include" to "**/*.kt"))
val ktlintCheck by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Check Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("src/**/*.kt", "build.gradle.kts")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
tasks.named("check") {
    dependsOn(ktlintCheck)
}
val ktlintFormat by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Fix Kotlin code style deviations."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/**/*.kt", "build.gradle.kts")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
