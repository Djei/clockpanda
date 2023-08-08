plugins {
    id("stream.pal.kotlin-application-conventions")
}

dependencies {
    implementation("com.google.api-client:google-api-client:1.33.2")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-1.32.1")
}