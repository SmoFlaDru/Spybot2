plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("gg.jte.gradle") version "3.2.3"
    id("com.gorylenko.gradle-git-properties") version "4.0.1"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${providers.gradleProperty("springBootVersion").get()}")
    }
}

dependencies {
    implementation(project(":spybot-core"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.webauthn4j:webauthn4j-core:0.31.0.RELEASE")
    implementation("gg.jte:jte:${providers.gradleProperty("jteVersion").get()}")
    implementation("gg.jte:jte-spring-boot-starter-4:${providers.gradleProperty("jteVersion").get()}")
    compileOnly("gg.jte:jte-kotlin:${providers.gradleProperty("jteVersion").get()}")
    implementation(kotlin("reflect"))
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:${providers.gradleProperty("sentryVersion").get()}")
    implementation("io.sentry:sentry-logback:${providers.gradleProperty("sentryVersion").get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:${providers.gradleProperty("testcontainersVersion").get()}")
    testImplementation("org.testcontainers:postgresql:${providers.gradleProperty("testcontainersVersion").get()}")
}

val frontendOutputDir = layout.projectDirectory.dir("../frontend/output")
val legacyStaticDir = layout.projectDirectory.dir("../spybot/static")
val generatedLegacyStaticDir = layout.buildDirectory.dir("generated-resources/legacy-static")
val generatedFrontendStaticDir = layout.buildDirectory.dir("generated-resources/frontend-static")

val prepareLegacyStaticAssets =
    tasks.register<Sync>("prepareLegacyStaticAssets") {
        from(legacyStaticDir)
        into(generatedLegacyStaticDir)
    }

val prepareFrontendAssets =
    tasks.register<Sync>("prepareFrontendAssets") {
        onlyIf {
            frontendOutputDir.file("main.js").asFile.exists() && frontendOutputDir.file("main.css").asFile.exists()
        }
        from(frontendOutputDir)
        into(generatedFrontendStaticDir)
    }

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(prepareLegacyStaticAssets) {
        into("static")
    }
    from(prepareFrontendAssets) {
        into("static")
    }
    from(rootProject.file("CHANGELOG.md"))
}

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir(".git"))
}

springBoot {
    buildInfo()
}

jte {
    precompile()
}

tasks.register("verifyFrontendAssets") {
    doLast {
        val requiredAssets =
            listOf(
                frontendOutputDir.file("main.js").asFile,
                frontendOutputDir.file("main.css").asFile,
            )
        val missing = requiredAssets.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Missing frontend build artifacts:")
                    missing.forEach { appendLine(" - ${it.absolutePath}") }
                    append(
                        "Run `npm ci && npm run package` in `frontend/`, or build via Docker which now performs the frontend packaging step.",
                    )
                },
            )
        }
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(tasks.named("precompileJte"))
    classpath += files(layout.projectDirectory.dir("jte-classes"))
}

tasks.named<Test>("test") {
    dependsOn(tasks.named("precompileJte"))
    classpath += files(layout.projectDirectory.dir("jte-classes"))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(tasks.named("precompileJte"), tasks.named("verifyFrontendAssets"))
    from(
        fileTree(layout.projectDirectory.dir("jte-classes")) {
            include("**/*.class")
            include("**/*.bin")
        },
    ) {
        into("BOOT-INF/classes")
    }
    archiveFileName.set("app.jar")
}
