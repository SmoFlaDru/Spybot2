import nu.studer.gradle.jooq.JooqEdition
import org.jooq.meta.jaxb.Logging
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
    id("nu.studer.jooq")
}

val jooqVersion = providers.gradleProperty("jooqVersion").get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${providers.gradleProperty("springBootVersion").get()}")
    }
}

dependencies {
    api("org.springframework:spring-context")
    api("org.springframework.security:spring-security-core")
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
    implementation(kotlin("reflect"))

    jooqGenerator("org.postgresql:postgresql")
    // Keep generator classpath versions aligned to avoid codegen runtime NoSuchMethodError
    jooqGenerator("org.jooq:jooq:${jooqVersion}")
    jooqGenerator("org.jooq:jooq-meta:${jooqVersion}")
    jooqGenerator("org.jooq:jooq-codegen:${jooqVersion}")
    jooqGenerator("org.jooq:jooq-meta-extensions:${jooqVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    named("main") {
        resources.srcDir("src/main/resources")
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

jooq {
    version.set(jooqVersion)
    edition.set(JooqEdition.OSS)
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                logging = Logging.WARN
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        inputSchema = "PUBLIC"
                        properties.addAll(
                            listOf(
                                org.jooq.meta.jaxb.Property().withKey("scripts").withValue("src/main/resources/db/migration/V1__baseline.sql"),
                                org.jooq.meta.jaxb.Property().withKey("sort").withValue("flyway"),
                                org.jooq.meta.jaxb.Property().withKey("defaultNameCase").withValue("lower"),
                            ),
                        )
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "com.spybot.jooq"
                        directory = layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.absolutePath
                    }
                }
            }
        }
    }
}

tasks.named("generateJooq") {
    enabled = true
}

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(tasks.named("generateJooq"))
}
