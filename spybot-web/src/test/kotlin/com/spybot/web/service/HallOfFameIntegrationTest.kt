package com.spybot.web.service

import com.spybot.core.service.SpybotQueryService
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
 * hallOfFame() is one analytical query (CTE + filtered aggregates + outer join); its shape only
 * means something against a real Postgres, so this runs on a Testcontainers database like
 * [LikedNameServiceIntegrationTest].
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HallOfFameIntegrationTest {
    @Autowired
    lateinit var queryService: SpybotQueryService

    @Autowired
    lateinit var dsl: DSLContext

    private fun newUser(
        name: String,
        onlineHours: Double,
    ): Long {
        val mergedUserId = dsl.fetchOne("insert into spybot_mergeduser (password, name) values ('', ?) returning id", name)!!.get(0, Long::class.java)
        val tsUserId = dsl.fetchOne("insert into tsuser (name, clientid, merged_user_id) values (?, 0, ?) returning id", name, mergedUserId)!!.get(0, Int::class.java)
        dsl.execute("insert into tschannel (id, name, \"order\", pid) values (1, 'Lobby', 0, 0) on conflict (id) do nothing")
        dsl.execute(
            "insert into tsuseractivity (tsuserid, starttime, endtime, cid) values (?, now() - make_interval(secs => ?), now(), 1)",
            tsUserId,
            onlineHours * 3600,
        )
        return mergedUserId
    }

    private fun award(
        mergedUserId: Long,
        points: Int,
    ) {
        dsl.execute("insert into spybot_award (points, merged_user_id) values (?, ?)", points, mergedUserId)
    }

    @Test
    fun `ranks by online time and counts gold, silver and bronze awards per user`() {
        val alice = newUser("Alice", onlineHours = 10.0)
        val bob = newUser("Bob", onlineHours = 20.0)
        val carol = newUser("Carol", onlineHours = 5.0)
        award(alice, 3)
        award(alice, 3)
        award(alice, 1)
        award(bob, 2)

        val entries = queryService.hallOfFame().filter { it.userId in setOf(alice, bob, carol) }

        assertEquals(listOf("Bob", "Alice", "Carol"), entries.map { it.user })
        val byId = entries.associateBy { it.userId }
        assertEquals(Triple(2, 0, 1), byId.getValue(alice).let { Triple(it.numGoldAwards, it.numSilverAwards, it.numBronzeAwards) })
        assertEquals(Triple(0, 1, 0), byId.getValue(bob).let { Triple(it.numGoldAwards, it.numSilverAwards, it.numBronzeAwards) })
        assertEquals(Triple(0, 0, 0), byId.getValue(carol).let { Triple(it.numGoldAwards, it.numSilverAwards, it.numBronzeAwards) }, "no awards must count as zero, not drop the user")
        assertEquals(20.0 * 3600, byId.getValue(bob).time, 5.0)
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
