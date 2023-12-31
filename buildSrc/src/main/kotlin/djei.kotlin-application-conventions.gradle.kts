import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("djei.kotlin-common-conventions")
    kotlin("plugin.serialization")
    // Spring plugins
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("ch.qos.logback:logback-core:1.4.11")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Web jars
    implementation("org.webjars:jquery:3.7.0")
    implementation("org.webjars:bootstrap:5.3.1")
    implementation("org.webjars:webjars-locator:0.47")

    // .env
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // Spring testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks {
    // By default, we do not want sub-projects to generate a boot jar or boot run task.
    // Only the main app application will explicitly enable these tasks.
    getByName<BootJar>("bootJar") {
        enabled = false
    }
    getByName<BootRun>("bootRun") {
        enabled = false
    }
}