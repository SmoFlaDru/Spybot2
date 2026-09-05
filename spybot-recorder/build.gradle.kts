plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${providers.gradleProperty("springBootVersion").get()}")
    }
}

// Resolves the Sentry OpenTelemetry javaagent jar so it can be copied into the Docker image,
// keeping its version in lockstep with sentryVersion instead of a hardcoded download URL.
val sentryAgent by configurations.creating

dependencies {
    implementation(project(":spybot-core"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(kotlin("reflect"))
    implementation("io.sentry:sentry-spring-boot-4:${providers.gradleProperty("sentryVersion").get()}")
    implementation("io.sentry:sentry-logback:${providers.gradleProperty("sentryVersion").get()}")
    sentryAgent("io.sentry:sentry-opentelemetry-agent:${providers.gradleProperty("sentryVersion").get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Jar>("jar") {
    enabled = false
}

val copySentryAgent =
    tasks.register<Copy>("copySentryAgent") {
        from(sentryAgent)
        into(layout.buildDirectory.dir("sentry-agent"))
        rename { "sentry-opentelemetry-agent.jar" }
    }

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(copySentryAgent)
    archiveFileName.set("app.jar")
}
