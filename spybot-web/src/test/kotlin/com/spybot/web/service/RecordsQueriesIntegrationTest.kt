package com.spybot.web.service

import com.spybot.core.service.RecordsQueries
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The records are window functions, DISTINCT ON and a sweep line over the activity history -
 * shapes that only mean something against a real Postgres, so this runs on Testcontainers like
 * [HallOfFameIntegrationTest]. Every test starts from an empty activity table so the top-5
 * lists are fully determined by what the test inserts.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RecordsQueriesIntegrationTest {
    @Autowired
    lateinit var recordsQueries: RecordsQueries

    @Autowired
    lateinit var dsl: DSLContext

    private val lobby = 1
    private val afk = 2

    @BeforeEach
    fun cleanSlate() {
        dsl.execute("delete from tsuseractivity")
        dsl.execute("delete from hourlyactivity")
        dsl.execute("insert into tschannel (id, name, \"order\", pid) values (?, 'Lobby', 0, 0) on conflict (id) do nothing", lobby)
        dsl.execute("insert into tschannel (id, name, \"order\", pid) values (?, 'AFK', 1, 0) on conflict (id) do nothing", afk)
    }

    private fun newUser(name: String): Pair<Long, Int> {
        val mergedUserId =
            dsl
                .fetchOne("insert into spybot_mergeduser (password, name) values ('', ?) returning id", name)!!
                .get(0, Long::class.java)
        val tsUserId =
            dsl
                .fetchOne("insert into tsuser (name, clientid, merged_user_id) values (?, 0, ?) returning id", name, mergedUserId)!!
                .get(0, Int::class.java)
        return mergedUserId to tsUserId
    }

    private fun session(
        tsUserId: Int,
        start: OffsetDateTime,
        hours: Double,
        channel: Int = lobby,
        open: Boolean = false,
    ) {
        // jOOQ binds OffsetDateTime as text in plain SQL, so cast; a null end stays an open session.
        dsl.execute(
            "insert into tsuseractivity (tsuserid, starttime, endtime, cid) values (?, ?::timestamptz, ?::timestamptz, ?)",
            tsUserId,
            start.toString(),
            if (open) null else start.plusSeconds((hours * 3600).toLong()).toString(),
            channel,
        )
    }

    private fun at(
        day: LocalDate,
        hour: Int,
    ): OffsetDateTime = day.atTime(hour, 0).atOffset(ZoneOffset.UTC)

    @Test
    fun `streaks count consecutive days, current ones must reach yesterday`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val (alice, aliceTs) = newUser("Alice")
        val (bob, bobTs) = newUser("Bob")
        val (carol, carolTs) = newUser("Carol")
        // Alice: 3 days in a row long ago, then a 2-day run that reaches today.
        for (d in 0..2) session(aliceTs, at(today.minusDays(40L + d), 20), 1.0)
        session(aliceTs, at(today.minusDays(1), 20), 1.0)
        session(aliceTs, at(today, 8), 1.0)
        // Bob: days 1, 2, 4 -> best run is 2, and it ended long ago.
        session(bobTs, at(today.minusDays(30), 20), 1.0)
        session(bobTs, at(today.minusDays(29), 20), 1.0)
        session(bobTs, at(today.minusDays(27), 20), 1.0)
        // Carol: only AFK, which never counts.
        for (d in 0..5) session(carolTs, at(today.minusDays(d.toLong()), 20), 1.0, channel = afk)

        val records = recordsQueries.records()

        assertEquals(
            listOf(alice to 3, bob to 2),
            records.longestStreaks.map {
                it.userId to it.length
            },
            "each user's best run, longest first",
        )
        assertEquals(today.minusDays(42), records.longestStreaks.first().startDay)
        assertEquals(today.minusDays(40), records.longestStreaks.first().endDay)
        assertEquals(listOf(alice to 2), records.currentStreaks.map { it.userId to it.length }, "only Alice's run reaches yesterday")
        assertTrue(records.longestStreaks.none { it.userId == carol }, "AFK-only days are not a streak")
    }

    @Test
    fun `longest sessions are one per user, closed and not AFK`() {
        val day = LocalDate.of(2024, 5, 4)
        val (alice, aliceTs) = newUser("Alice")
        val (bob, bobTs) = newUser("Bob")
        session(aliceTs, at(day, 10), 9.5)
        session(aliceTs, at(day.minusDays(3), 10), 30.0, channel = afk) // long, but AFK
        session(aliceTs, at(day.plusDays(1), 10), 3.0)
        session(bobTs, at(day, 10), 2.0)
        session(bobTs, at(day, 13), 12.0, channel = afk) // AFK doesn't count as a session record
        session(bobTs, at(day.plusDays(2), 10), 5.0, open = true) // still running: not a record yet

        val records = recordsQueries.records()

        assertEquals(listOf(alice, bob), records.longestSessions.map { it.userId })
        assertEquals(9.5 * 3600, records.longestSessions[0].value, 1.0)
        assertEquals(day, records.longestSessions[0].date)
        assertEquals(2.0 * 3600, records.longestSessions[1].value, 1.0)

        val bests = recordsQueries.personalBests(alice)
        assertEquals(9.5 * 3600, bests.longestSessionSeconds!!, 1.0)
        assertEquals(day, bests.longestSessionDate)
    }

    @Test
    fun `best week sums a user's sessions per Monday-based week`() {
        val monday = LocalDate.of(2024, 5, 6)
        val (alice, aliceTs) = newUser("Alice")
        val (bob, bobTs) = newUser("Bob")
        session(aliceTs, at(monday, 18), 4.0)
        session(aliceTs, at(monday.plusDays(6), 18), 4.0) // Sunday, same week
        session(aliceTs, at(monday.plusDays(7), 18), 5.0) // next Monday: a new week
        session(bobTs, at(monday, 18), 6.0)
        session(bobTs, at(monday.plusDays(1), 18), 6.0, channel = afk)
        session(bobTs, at(monday.plusDays(2), 18), 6.0, open = true) // still open (or never closed): not counted

        val records = recordsQueries.records()

        assertEquals(listOf(alice, bob), records.bestWeeks.map { it.userId })
        assertEquals(8.0, records.bestWeeks[0].value, 0.001)
        assertEquals(6.0, records.bestWeeks[1].value, 0.001, "Bob's AFK and open sessions don't count")
        assertEquals(monday, records.bestWeeks[0].date)

        val bests = recordsQueries.personalBests(alice)
        assertEquals(8.0, bests.bestWeekHours!!, 0.001)
        assertEquals(monday, bests.bestWeekStart)
    }

    @Test
    fun `peak users counts overlapping sessions, AFK included, without double counting a channel move`() {
        val day = LocalDate.of(2024, 5, 4)
        val (_, aliceTs) = newUser("Alice")
        val (_, bobTs) = newUser("Bob")
        val (_, carolTs) = newUser("Carol")
        session(aliceTs, at(day, 10), 4.0) // 10-14
        session(bobTs, at(day, 12), 1.0) // 12-13
        session(bobTs, at(day, 13), 2.0, channel = afk) // 13-15, a move: still one Bob
        session(carolTs, at(day, 13), 1.0) // 13-14 -> three online 13-14
        session(carolTs, at(day.plusDays(1), 10), 1.0) // alone the next day

        val peak = recordsQueries.records().peakUsers

        assertNotNull(peak)
        assertEquals(3, peak!!.users)
        assertEquals(at(day, 13).toInstant(), peak.at.toInstant())
    }

    @Test
    fun `busiest day comes from the hourly snapshots`() {
        dsl.execute(
            "insert into hourlyactivity (datetime, activity_hours) values ('2024-05-04T20:00:00Z', 5.0), ('2024-05-04T21:00:00Z', 6.0), ('2024-05-05T20:00:00Z', 10.0)",
        )

        val busiest = recordsQueries.records().busiestDay

        assertNotNull(busiest)
        assertEquals(LocalDate.of(2024, 5, 4), busiest!!.day)
        assertEquals(11.0, busiest.hours, 0.001)
    }

    @Test
    fun `empty history gives empty lists and no peak or busiest day`() {
        val records = recordsQueries.records()

        assertTrue(records.longestStreaks.isEmpty())
        assertTrue(records.longestSessions.isEmpty())
        assertNull(records.peakUsers)
        assertNull(records.busiestDay)
        val (nobody, _) = newUser("Nobody")
        assertNull(recordsQueries.personalBests(nobody).longestSessionSeconds)
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
