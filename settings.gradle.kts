pluginManagement {
    plugins {
        // The JTE Gradle plugin, jte-runtime and jte-kotlin must be the same release; jteVersion
        // in gradle.properties is the one place to bump it.
        id("gg.jte.gradle") version providers.gradleProperty("jteVersion").get()
    }
}

rootProject.name = "spybot2-rewrite"

include(
    "spybot-core",
    "spybot-web",
    "spybot-recorder",
)
