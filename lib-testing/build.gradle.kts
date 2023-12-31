plugins {
    id("djei.kotlin-application-conventions")
}

dependencies {
    implementation("org.jooq:jooq")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.security:spring-security-test")
    // This is needed for us to auto-execute the flyway migration scripts on our test database when we run them
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
}
