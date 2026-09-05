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

dependencies {
    implementation(project(":spybot-core"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(kotlin("reflect"))
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:${providers.gradleProperty("sentryVersion").get()}")
    implementation("io.sentry:sentry-logback:${providers.gradleProperty("sentryVersion").get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
