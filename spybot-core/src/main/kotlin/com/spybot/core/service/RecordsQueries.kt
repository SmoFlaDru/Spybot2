package com.spybot.core.service

import com.spybot.core.jooq.double
import com.spybot.core.jooq.int
import com.spybot.core.jooq.localDate
import com.spybot.core.jooq.long
import com.spybot.core.jooq.offsetDateTime
import com.spybot.core.jooq.string
import com.spybot.core.model.BusiestDayRecord
import com.spybot.core.model.PeakUsersRecord
import com.spybot.core.model.PersonalBests
import com.spybot.core.model.RecordsView
import com.spybot.core.model.StreakRecord
import com.spybot.core.model.UserRecord
import com.spybot.core.teamspeak.unescapeTeamSpeak
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * All-time records for the hall of fame: longest streaks, longest sessions, best weeks, the
 * most users online at once and the busiest day, plus a user's own bests for their page.
 *
 * Every query is a scan over the whole activity history, so [records] is meant to be computed
 * by the hourly job and cached, not run per request.
 */
@Service
class RecordsQueries(
    private val dsl: DSLContext,
) {
    fun records(): RecordsView =
        RecordsView(
            longestStreaks = streaks(currentOnly = false),
            currentStreaks = streaks(currentOnly = true),
            longestSessions = longestSessions(),
            bestWeeks = bestWeeks(),
            peakUsers = peakUsers(),
            busiestDay = busiestDay(),
            computedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )

    fun personalBests(userId: Long): PersonalBests {
        val session =
            dsl.fetchOne(
                """
                $SESSIONS_CTE
                SELECT EXTRACT(EPOCH FROM (endtime - starttime)) AS seconds, starttime::date AS day
                FROM sessions
                WHERE NOT afk AND closed AND user_id = ?
                ORDER BY seconds DESC
                LIMIT 1
                """.trimIndent(),
                userId,
            )
        val week =
            dsl.fetchOne(
                """
                $SESSIONS_CTE
                SELECT DATE_TRUNC('week', starttime)::date AS week_start, $HOURS_SUM AS hours
                FROM sessions
                WHERE NOT afk AND user_id = ?
                GROUP BY week_start
                ORDER BY hours DESC
                LIMIT 1
                """.trimIndent(),
                userId,
            )
        return PersonalBests(
            longestSessionSeconds = session?.double("seconds"),
            longestSessionDate = session?.localDate("day"),
            bestWeekHours = week?.double("hours"),
            bestWeekStart = week?.localDate("week_start"),
        )
    }

    /**
     * Gaps-and-islands over the distinct days each user was online: subtracting a day's row
     * number (per user, ordered by day) from the day itself gives the same value for every day
     * of one unbroken run. A session counts for the day it started on, like the streak on the
     * user page. Each user appears once with their best run; current streaks are those still
     * alive today or yesterday (a user can only have one of those).
     */
    private fun streaks(currentOnly: Boolean): List<StreakRecord> =
        dsl
            .fetch(
                """
                $SESSIONS_CTE,
                days AS (
                    SELECT DISTINCT user_id, starttime::date AS day
                    FROM sessions
                    WHERE NOT afk
                ),
                grouped AS (
                    SELECT user_id, day, day - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY day))::int AS island
                    FROM days
                ),
                streaks AS (
                    SELECT user_id, MIN(day) AS start_day, MAX(day) AS end_day, COUNT(*) AS length
                    FROM grouped
                    GROUP BY user_id, island
                ),
                per_user AS (
                    SELECT DISTINCT ON (user_id) user_id, start_day, end_day, length
                    FROM streaks
                    ${if (currentOnly) "WHERE end_day >= CURRENT_DATE - 1" else ""}
                    ORDER BY user_id, length DESC, end_day DESC
                )
                SELECT per_user.user_id, mu.name AS user_name, start_day, end_day, length
                FROM per_user
                JOIN spybot_mergeduser mu ON mu.id = per_user.user_id
                ORDER BY length DESC, end_day DESC
                LIMIT $TOP_N
                """.trimIndent(),
            ).map {
                StreakRecord(
                    userId = it.long("user_id"),
                    userName = unescapeTeamSpeak(it.string("user_name")),
                    startDay = it.localDate("start_day")!!,
                    endDay = it.localDate("end_day")!!,
                    length = it.int("length"),
                )
            }

    /** Each user's single longest session, top [TOP_N] users. */
    private fun longestSessions(): List<UserRecord> =
        dsl
            .fetch(
                """
                $SESSIONS_CTE,
                per_user AS (
                    SELECT DISTINCT ON (user_id)
                        user_id, EXTRACT(EPOCH FROM (endtime - starttime)) AS seconds, starttime::date AS day
                    FROM sessions
                    WHERE NOT afk AND closed
                    ORDER BY user_id, seconds DESC
                )
                SELECT per_user.user_id, mu.name AS user_name, seconds AS value, day
                FROM per_user
                JOIN spybot_mergeduser mu ON mu.id = per_user.user_id
                ORDER BY seconds DESC
                LIMIT $TOP_N
                """.trimIndent(),
            ).map { userRecord(it) }

    /** Each user's best calendar week (Monday-based, a session counts for the week it started in), top [TOP_N] users. */
    private fun bestWeeks(): List<UserRecord> =
        dsl
            .fetch(
                """
                $SESSIONS_CTE,
                weeks AS (
                    SELECT user_id, DATE_TRUNC('week', starttime)::date AS week_start, $HOURS_SUM AS hours
                    FROM sessions
                    WHERE NOT afk
                    GROUP BY user_id, week_start
                ),
                per_user AS (
                    SELECT DISTINCT ON (user_id) user_id, week_start AS day, hours
                    FROM weeks
                    ORDER BY user_id, hours DESC
                )
                SELECT per_user.user_id, mu.name AS user_name, hours AS value, day
                FROM per_user
                JOIN spybot_mergeduser mu ON mu.id = per_user.user_id
                ORDER BY hours DESC
                LIMIT $TOP_N
                """.trimIndent(),
            ).map { userRecord(it) }

    /**
     * Sweep line over session starts (+1) and ends (-1): the running sum is the number of open
     * sessions at each instant, and its maximum the most users online at once. Ends sort before
     * starts at the same instant so a channel move (one row ends exactly as the next begins)
     * doesn't count that user twice. AFK channels count here - being AFK is still being online.
     */
    private fun peakUsers(): PeakUsersRecord? =
        dsl
            .fetchOne(
                """
                $SESSIONS_CTE,
                events AS (
                    SELECT starttime AS at, 1 AS delta FROM sessions
                    UNION ALL
                    SELECT endtime AS at, -1 AS delta FROM sessions
                ),
                running AS (
                    SELECT at, SUM(delta) OVER (ORDER BY at, delta) AS online
                    FROM events
                )
                SELECT at, online
                FROM running
                ORDER BY online DESC, at ASC
                LIMIT 1
                """.trimIndent(),
            )?.let {
                PeakUsersRecord(users = it.int("online"), at = it.offsetDateTime("at") ?: return null)
            }

    /** The day with the most summed-up online hours, from the hourly snapshots (which the job has kept since it exists). */
    private fun busiestDay(): BusiestDayRecord? =
        dsl
            .fetchOne(
                """
                SELECT datetime::date AS day, SUM(activity_hours) AS hours
                FROM hourlyactivity
                GROUP BY day
                ORDER BY hours DESC
                LIMIT 1
                """.trimIndent(),
            )?.let {
                BusiestDayRecord(day = it.localDate("day") ?: return null, hours = it.double("hours"))
            }

    private fun userRecord(row: org.jooq.Record): UserRecord =
        UserRecord(
            userId = row.long("user_id"),
            userName = unescapeTeamSpeak(row.string("user_name")),
            value = row.double("value"),
            date = row.localDate("day")!!,
        )

    private companion object {
        const val TOP_N = 5

        /**
         * The sessions every record is computed from. Everything before 2016 is dropped like on
         * the user page (older data isn't trustworthy), and so are sessions longer than a day:
         * the recorder used to leave sessions open or close them late after a lost connection,
         * and a five-day "session" would otherwise be the record for everything. An open session
         * ends now; one open for more than a day is that same bug and is skipped too.
         */
        val SESSIONS_CTE =
            """
            WITH sessions AS (
                SELECT
                    tu.merged_user_id AS user_id,
                    a.starttime,
                    COALESCE(a.endtime, NOW()) AS endtime,
                    a.endtime IS NOT NULL AS closed,
                    c.name IN ('bei\sBedarf\sanstupsen', 'AFK') AS afk
                FROM tsuseractivity a
                JOIN tsuser tu ON a.tsuserid = tu.id
                JOIN tschannel c ON a.cid = c.id
                WHERE tu.merged_user_id IS NOT NULL
                    AND a.starttime > MAKE_DATE(2016, 1, 1)
                    AND COALESCE(a.endtime, NOW()) - a.starttime <= INTERVAL '24 hours'
            )
            """.trimIndent()

        const val HOURS_SUM = "SUM(EXTRACT(EPOCH FROM (endtime - starttime))) / 3600"
    }
}
