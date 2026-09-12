package com.spybot.web.service

import com.spybot.core.service.StatisticsQueries
import org.jooq.DSLContext
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
 * liveClients() nests each client's Steam IDs with a MULTISET, which jOOQ emulates through
 * Postgres JSON; that only means something against a real database, so this runs on a
 * Testcontainers Postgres like [HallOfFameIntegrationTest].
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiveClientsIntegrationTest {
    @Autowired
    lateinit var statisticsQueries: StatisticsQueries

    @Autowired
    lateinit var dsl: DSLContext

    private fun connectedUser(
        name: String,
        channel: Int,
        steamIds: List<Long>,
    ): Long {
        val mergedUserId = dsl.fetchOne("insert into spybot_mergeduser (password, name) values ('', ?) returning id", name)!!.get(0, Long::class.java)
        val tsUserId = dsl.fetchOne("insert into tsuser (name, clientid, merged_user_id) values (?, 0, ?) returning id", name, mergedUserId)!!.get(0, Int::class.java)
        dsl.execute("insert into tschannel (id, name, \"order\", pid) values (?, ?, ?, 0) on conflict (id) do nothing", channel, "Channel $channel", channel)
        dsl.execute("insert into tsuseractivity (tsuserid, starttime, endtime, cid) values (?, now(), null, ?)", tsUserId, channel)
        steamIds.forEach { dsl.execute("insert into spybot_steamid (steam_id, merged_user_id) values (?, ?)", it, mergedUserId) }
        return mergedUserId
    }

    @Test
    fun `live clients carry their Steam IDs as a nested list, empty when they have none`() {
        val alice = connectedUser("Alice", channel = 1, steamIds = listOf(76561198000000001, 76561198000000002))
        val bob = connectedUser("Bob", channel = 2, steamIds = emptyList())

        val (channels, clients) = statisticsQueries.liveClients()
        val byUser = clients.associateBy { it.mergedUserId }

        assertEquals(listOf("76561198000000001", "76561198000000002"), byUser.getValue(alice).steamIds)
        assertEquals(emptyList<String>(), byUser.getValue(bob).steamIds)
        assertEquals(1, byUser.getValue(alice).channelId)
        assertEquals("Alice", byUser.getValue(alice).name)
        assertEquals(listOf(1, 2), channels.map { it.id }.filter { it in setOf(1, 2) })
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
