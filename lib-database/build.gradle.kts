plugins {
    id("djei.kotlin-application-conventions")
    id("nu.studer.jooq") version "8.2.1"
    id("org.flywaydb.flyway") version "9.21.1"
}

dependencies {
    implementation("org.jooq:jooq")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    jooqGenerator("org.xerial:sqlite-jdbc:3.42.0.0")

    testImplementation(project(":lib-testing"))
}

val dbUrl = "jdbc:sqlite:file:${System.getProperty("user.dir")}/db.sqlite3"

flyway {
    url = dbUrl
    validateMigrationNaming = true
    cleanDisabled = false
}

jooq {
    val jooqGenerationExclusions = listOf(
        // Flyway tables
        "flyway_.*",
    )

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN

                jdbc.apply {
                    driver = "org.sqlite.JDBC"
                    url = dbUrl
                }

                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.sqlite.SQLiteDatabase"
                        includes = ".*"
                        excludes = jooqGenerationExclusions.joinToString(separator = "|")
                        isIncludeExcludePackageRoutines = true
                        forcedTypes = listOf()
                    }
                    generate.apply {
                        isDaos = false
                        isRecords = true
                        isPojos = false
                        isImmutablePojos = false
                    }
                    target.apply {
                        packageName = "djei.clockpanda.jooq"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

tasks {
    compileKotlin {
        // Ensure we generate the jOOQ sources before compiling Kotlin
        dependsOn("generateJooq")
    }

    val flywayMigrateTask = named<org.flywaydb.gradle.task.FlywayMigrateTask>("flywayMigrate")
    named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
        // ensure database schema has been prepared by Flyway before generating the jOOQ sources
        dependsOn(flywayMigrateTask)

        // declare Flyway migration scripts as inputs on the jOOQ task
        inputs.files(fileTree("src/main/resources/db/migration"))
            .withPropertyName("migrations")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // make jOOQ task participate in incremental builds (and build caching)
        allInputsDeclared.set(true)
    }
}
