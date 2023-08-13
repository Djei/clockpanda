plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    implementation(project(":lib-database"))
    implementation(project(":lib-logging"))

    implementation("org.jooq:jooq")
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-1.32.1")

    testImplementation(project(":lib-testing"))
}
