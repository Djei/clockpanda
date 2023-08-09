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
