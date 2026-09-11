package com.spybot.web.service

import com.spybot.core.service.LikedNameService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The like counter is an INSERT ... ON CONFLICT upsert; that only means something against a real
 * Postgres with the Flyway schema applied, so this runs on a Testcontainers database like
 * [com.spybot.web.SpybotWebApplicationContextTest].
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikedNameServiceIntegrationTest {
    @Autowired
    lateinit var service: LikedNameService

    @Test
    fun `liking the same name twice bumps its counter instead of adding a row`() {
        val first = service.like("Carry Potter", "Harry Potter", "carry")
        val second = service.like("Carry Potter", "Harry Potter", "carry")

        assertEquals(1, first.likes)
        assertEquals(2, second.likes)
        assertEquals(1, service.top(30).count { it.displayName == "Carry Potter" })
    }

    @Test
    fun `top list is ordered by likes and capped`() {
        repeat(3) { service.like("Nuke Skywalker", "Luke Skywalker", "nuke") }
        service.like("Gregor Easy", "Gregor Gysi", "easy")

        val top = service.top(30)

        assertEquals("Nuke Skywalker", top.first().displayName)
        assertEquals(3, top.first().likes)
        assertEquals(1, service.top(1).size)
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
