plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    implementation(project(":service-authnz"))
    implementation(project(":service-scheduling"))
}

tasks {
    getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = true
    }

    getByName<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
        enabled = true
    }
}
