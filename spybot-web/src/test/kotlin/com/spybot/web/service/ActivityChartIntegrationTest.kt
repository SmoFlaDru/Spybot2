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
import java.time.LocalDate

/**
 * The home page's activity chart labels every bar, so activityChart() must return one point per
 * day of the selected range - days without any activity included, as zeros - or the category axis
 * would silently close the gaps. That is SQL (generate_series + outer joins), so this runs on a
 * Testcontainers database like [HallOfFameIntegrationTest].
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ActivityChartIntegrationTest {
    @Autowired
    lateinit var statisticsQueries: StatisticsQueries

    @Autowired
    lateinit var dsl: DSLContext

    private fun activity(
        tsUserId: Int,
        daysAgo: Int,
        hours: Int,
    ) {
        dsl.execute(
            "insert into tsuseractivity (tsuserid, starttime, endtime, cid) " +
                "values (?, current_date - make_interval(days => ?) + interval '10 hours', " +
                "current_date - make_interval(days => ?) + interval '10 hours' + make_interval(hours => ?), 1)",
            tsUserId,
            daysAgo,
            daysAgo,
            hours,
        )
    }

    @Test
    fun `returns one point per day of the range with zeros for days without activity`() {
        val tsUserId =
            dsl
                .fetchOne("insert into tsuser (name, clientid) values ('Alice', 0) returning id")!!
                .get(0, Int::class.java)
        dsl.execute("insert into tschannel (id, name, \"order\", pid) values (1, 'Lobby', 0, 0) on conflict (id) do nothing")
        activity(tsUserId, daysAgo = 2, hours = 1)
        activity(tsUserId, daysAgo = 5, hours = 2)

        val points = statisticsQueries.activityChart(7).points

        // "Last 7 days" covers today and the 7 days before it.
        assertEquals(8, points.size)
        val today = dsl.fetchOne("select current_date")!!.get(0, LocalDate::class.java)
        assertEquals((7 downTo 0).map { today.minusDays(it.toLong()).toString() }, points.map { it.date })
        assertEquals(
            mapOf(today.minusDays(2).toString() to 1.0, today.minusDays(5).toString() to 2.0),
            points.filter { it.activeHours > 0.0 }.associate { it.date to it.activeHours },
        )
        assertEquals(6, points.count { it.activeHours == 0.0 && it.afkHours == 0.0 })
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
