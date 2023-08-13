plugins {
    id("djei.kotlin-application-conventions")
    kotlin("plugin.noarg") version "1.8.22"
}

dependencies {
    implementation(project(":lib-database"))
    implementation(project(":lib-logging"))

    implementation("org.jooq:jooq")
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20230707-2.0.0")

    implementation(platform("ai.timefold.solver:timefold-solver-bom:1.0.0"))
    implementation("ai.timefold.solver:timefold-solver-core")

    testImplementation(project(":lib-testing"))
    testImplementation("ai.timefold.solver:timefold-solver-test")
}

noArg {
    annotation("djei.clockpanda.scheduling.optimization.NoArg")
}
