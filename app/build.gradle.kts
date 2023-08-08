plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    implementation(project(":service-authnz"))
    implementation(project(":service-scheduling"))

    implementation("org.apache.tomcat.embed:tomcat-embed-jasper")
}

tasks {
    getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = true
    }
    getByName<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
        enabled = true
    }
}
