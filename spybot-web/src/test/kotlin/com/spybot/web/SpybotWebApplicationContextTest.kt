package com.spybot.web

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Regression guard for startup-breaking issues (bad auto-configuration, a dependency version
 * mismatch, etc.) that no other test catches: every other test in this module mocks or scopes
 * out the full Spring context, so a broken ApplicationContext (e.g. the Sentry/Spring Boot 4
 * incompatibility this guards against) can pass the whole suite while the real app fails to boot.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpybotWebApplicationContextTest {
    @Test
    fun `application context loads`() {
        // Intentionally empty - @SpringBootTest already fails this test if the context can't
        // start.
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17.2")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
