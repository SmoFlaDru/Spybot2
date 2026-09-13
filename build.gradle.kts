import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.spring") version "2.4.20" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("nu.studer.jooq") version "10.2.1" apply false
}

allprojects {
    group = "com.spybot"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // The Spring Boot BOM (applied by io.spring.dependency-management) pins every Kotlin artifact
    // to the BOM's own Kotlin version - including the compiler the Kotlin Gradle plugin runs and
    // the stdlib we ship. Point it at the plugin's version instead, or a newer plugin ends up
    // driving an older compiler ("GRANULARITY is available only since 2.3.0").
    extra["kotlin.version"] = getKotlinPluginVersion()

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(24)
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
