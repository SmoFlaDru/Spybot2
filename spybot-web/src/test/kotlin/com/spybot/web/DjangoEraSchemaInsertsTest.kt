package com.spybot.web

import com.spybot.core.service.SpybotQueryService
import com.spybot.jooq.tables.references.SPYBOT_AWARD
import com.spybot.jooq.tables.references.SPYBOT_NEWSEVENT
import com.spybot.jooq.tables.references.SPYBOT_QUEUEDCLIENTMESSAGE
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The production database was created by the old Django app, which kept column defaults in
 * Python rather than in the schema. A baselined Flyway database never runs V1, so production's
 * date columns have no DEFAULT even though V1 declares one - and an insert that relies on the
 * default fails with a not-null violation. This happened to the weekly awards job (Sentry
 * SPYBOT-44). Every insert must therefore set its dates explicitly; this test strips the
 * defaults the migrations would add and proves the inserts still work.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DjangoEraSchemaInsertsTest {
    @Autowired
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var queryService: SpybotQueryService

    @BeforeEach
    fun dropDefaultsLikeProduction() {
        dsl.execute("alter table spybot_award alter column date drop default")
        dsl.execute("alter table spybot_newsevent alter column date drop default")
        dsl.execute("alter table spybot_queuedclientmessage alter column date drop default")
    }

    @Test
    fun `weekly award inserts set their dates without relying on column defaults`() {
        val identity = queryService.createTeamSpeakIdentity("Alice", 42, "uid-alice")

        queryService.createAward(identity.mergedUserId, 3)
        queryService.createNewsEvent("Alice is user of the week", null)
        queryService.replaceQueuedMessage(identity.mergedUserId, "AWARD_USER_OF_WEEK", "You got an award")

        assertNotNull(
            dsl
                .select(SPYBOT_AWARD.DATE)
                .from(SPYBOT_AWARD)
                .fetchSingle()
                .value1(),
        )
        assertNotNull(
            dsl
                .select(SPYBOT_NEWSEVENT.DATE)
                .from(SPYBOT_NEWSEVENT)
                .fetchSingle()
                .value1(),
        )
        assertNotNull(
            dsl
                .select(SPYBOT_QUEUEDCLIENTMESSAGE.DATE)
                .from(SPYBOT_QUEUEDCLIENTMESSAGE)
                .fetchSingle()
                .value1(),
        )
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
