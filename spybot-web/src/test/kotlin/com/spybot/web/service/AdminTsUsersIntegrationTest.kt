package com.spybot.web.service

import com.spybot.core.service.AdminQueries
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Regression test for the admin user list crashing in production: most tsuser rows there have
 * no client id, which the query used to map as a NOT NULL column.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminTsUsersIntegrationTest {
    @Autowired
    lateinit var adminQueries: AdminQueries

    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `lists identities whose client id was never recorded`() {
        dsl.execute("insert into tsuser (name, clientid, iscurrentlyonline) values ('old-timer', null, false)")
        dsl.execute("insert into tsuser (name, clientid, iscurrentlyonline) values ('regular', 42, true)")

        val rows = adminQueries.adminTsUsers(null).associateBy { it.name }

        assertNull(rows.getValue("old-timer").clientId)
        assertEquals(42, rows.getValue("regular").clientId)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17.2")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
