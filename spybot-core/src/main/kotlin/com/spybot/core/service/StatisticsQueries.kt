package com.spybot.core.service

import com.spybot.core.jooq.boolean
import com.spybot.core.jooq.double
import com.spybot.core.jooq.int
import com.spybot.core.jooq.localDate
import com.spybot.core.jooq.long
import com.spybot.core.jooq.notNull
import com.spybot.core.jooq.offsetDateTime
import com.spybot.core.jooq.parseJsonArray
import com.spybot.core.jooq.string
import com.spybot.core.jooq.toEpochMillis
import com.spybot.core.model.ActiveUsersStat
import com.spybot.core.model.ActivityChartView
import com.spybot.core.model.ChannelPopularityEntry
import com.spybot.core.model.ChannelView
import com.spybot.core.model.DailyActivityPoint
import com.spybot.core.model.HallOfFameEntry
import com.spybot.core.model.LiveApiChannel
import com.spybot.core.model.LiveApiResponse
import com.spybot.core.model.LiveApiUser
import com.spybot.core.model.LiveClientView
import com.spybot.core.model.MonthActivityPoint
import com.spybot.core.model.RecentEventView
import com.spybot.core.model.RecentEventsPayload
import com.spybot.core.model.SelectorOption
import com.spybot.core.model.StreakView
import com.spybot.core.model.TimeRangeView
import com.spybot.core.model.TimelineEntry
import com.spybot.core.model.TimelineUserSeries
import com.spybot.core.model.TopUserWeek
import com.spybot.core.model.UserHeadline
import com.spybot.core.model.UserPageView
import com.spybot.core.model.WeekComparisonPoint
import com.spybot.core.model.WeekTrendView
import com.spybot.core.model.WidgetLegacyResponse
import com.spybot.core.teamspeak.unescapeTeamSpeak
import com.spybot.jooq.tables.references.HOURLYACTIVITY
import com.spybot.jooq.tables.references.SPYBOT_NEWSEVENT
import com.spybot.jooq.tables.references.TSCHANNEL
import com.spybot.jooq.tables.references.TSUSER
import com.spybot.jooq.tables.references.TSUSERACTIVITY
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Read-only analytics behind the home, timeline, hall of fame and user pages, plus the hourly activity snapshot the scheduled job records. */
@Service
class StatisticsQueries(
    private val dsl: DSLContext,
    private val recordsQueries: RecordsQueries,
) {
    fun liveApi(): LiveApiResponse {
        val channels =
            dsl
                .select(TSCHANNEL.ID.notNull(), TSCHANNEL.NAME)
                .from(TSCHANNEL)
                .orderBy(TSCHANNEL.ORDER.asc())
                .fetch(mapping(::LiveApiChannel))
        val clients =
            dsl
                .select(TSUSER.NAME, TSUSERACTIVITY.CID.notNull())
                .from(TSUSERACTIVITY)
                .join(TSUSER)
                .on(TSUSER.ID.eq(TSUSERACTIVITY.TSUSERID))
                .where(TSUSERACTIVITY.ENDTIME.isNull)
                .fetch(mapping(::LiveApiUser))

        return LiveApiResponse(clients = clients, channels = channels)
    }

    fun widgetLegacy(): WidgetLegacyResponse {
        val active = mutableListOf<String?>()
        val inactive = mutableListOf<String?>()
        dsl
            .fetch(
                """
                select u.name as user_name, c.name as channel_name
                from tsuseractivity a
                join tsuser u on u.id = a.tsuserid
                join tschannel c on c.id = a.cid
                where a.endtime is null
                """.trimIndent(),
            ).forEach { record ->
                when (record.string("channel_name")) {
                    "bei Bedarf anstupsen", "AFK" -> inactive += record.get("user_name", String::class.java)
                    else -> active += record.get("user_name", String::class.java)
                }
            }

        return WidgetLegacyResponse(activeClients = active, inactiveClients = inactive)
    }

    fun liveClients(): Pair<List<ChannelView>, List<LiveClientView>> {
        val channels =
            dsl
                .select(TSCHANNEL.ID.notNull(), TSCHANNEL.NAME)
                .from(TSCHANNEL)
                .orderBy(TSCHANNEL.ORDER.asc())
                .fetch(mapping { id, name -> ChannelView(id, name?.let(::unescapeTeamSpeak)) })

        val clients =
            dsl
                .fetch(
                    """
                    select
                        a.cid as channel_id,
                        u.name,
                        u.merged_user_id,
                        coalesce(
                            (
                                select json_agg(s.steam_id::text)
                                from spybot_steamid s
                                where s.merged_user_id = u.merged_user_id
                            )::text,
                            '[]'
                        ) as steam_ids
                    from tsuseractivity a
                    join tsuser u on u.id = a.tsuserid
                    where a.endtime is null
                    """.trimIndent(),
                ).map {
                    LiveClientView(
                        channelId = it.int("channel_id"),
                        name = it.get("name", String::class.java),
                        mergedUserId = it.get("merged_user_id", Long::class.java),
                        steamIds = parseJsonArray(it.string("steam_ids")),
                    )
                }

        return channels to clients
    }

    fun activityChart(timeSpan: Int): ActivityChartView {
        val allowed = listOf(7, 14, 30, 90)
        val selected = allowed.firstOrNull { it == timeSpan } ?: allowed.first()
        val options = allowed.map { SelectorOption("Last $it days", it, it == selected) }
        val points =
            dsl
                .fetch(
                    """
                    WITH active_data AS (
                        SELECT
                            TO_CHAR(starttime, 'YYYY-MM-DD') AS date,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        WHERE
                            starttime > CURRENT_DATE - (? || ' days')::interval
                            AND endtime IS NOT NULL
                            AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                        GROUP BY date
                        ORDER BY date
                    ),
                    afk_data AS (
                        SELECT
                            TO_CHAR(starttime, 'YYYY-MM-DD') AS date,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        WHERE
                            starttime > CURRENT_DATE - (? || ' days')::interval
                            AND endtime IS NOT NULL
                            AND channel.name IN ('bei\sBedarf\sanstupsen', 'AFK')
                        GROUP BY date
                        ORDER BY date
                    )
                    SELECT active_data.date,
                           CAST(active_data.time_hours AS DOUBLE PRECISION) AS active_hours,
                           COALESCE(CAST(afk_data.time_hours AS DOUBLE PRECISION), 0) AS afk_hours
                    FROM active_data
                    LEFT OUTER JOIN afk_data ON active_data.date = afk_data.date
                    """.trimIndent(),
                    selected,
                    selected,
                ).map {
                    DailyActivityPoint(
                        date = it.string("date"),
                        activeHours = it.double("active_hours"),
                        afkHours = it.double("afk_hours"),
                    )
                }

        return ActivityChartView(points = points, options = options, activeOptionText = "Last $selected days")
    }

    fun timeOfDayHistogram(): List<Pair<String, Double>> =
        run {
            val hourField = DSL.field("to_char({0}, 'HH24')", String::class.java, HOURLYACTIVITY.DATETIME).`as`("hour")
            dsl
                .select(hourField, DSL.avg(HOURLYACTIVITY.ACTIVITY_HOURS).`as`("amplitude"))
                .from(HOURLYACTIVITY)
                .groupBy(hourField)
                .orderBy(hourField)
                .fetch { (it.get(hourField) ?: "") to ((it.get("amplitude") as Number?)?.toDouble() ?: 0.0) }
        }

    fun topUsersOfWeek(): List<TopUserWeek> =
        dsl
            .fetch(
                """
                WITH start_of_week AS (
                    SELECT DATE_TRUNC('week', CURRENT_DATE)::DATE AS date
                )
                SELECT
                    SUM(EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime))) / 3600 AS time,
                    mu.name AS user_name,
                    mu.id AS user_id
                FROM start_of_week, tsuseractivity
                INNER JOIN tsuser tu ON tsuserid = tu.id
                INNER JOIN spybot_mergeduser mu ON tu.merged_user_id = mu.id
                WHERE starttime > start_of_week.date
                GROUP BY mu.id
                ORDER BY time DESC
                LIMIT 3
                """.trimIndent(),
            ).map {
                TopUserWeek(
                    time = it.double("time"),
                    userName = it.string("user_name"),
                    userId = it.long("user_id"),
                )
            }

    fun activeUsersStat(): ActiveUsersStat {
        val record =
            dsl.fetchOne(
                """
                WITH start_of_week AS (
                    SELECT DATE_TRUNC('week', CURRENT_DATE)::DATE AS date
                )
                SELECT
                    COUNT(DISTINCT mu.id) AS users_this_week,
                    COUNT(DISTINCT mu.id) FILTER (WHERE starttime > CURRENT_DATE) AS users_today
                FROM start_of_week, tsuseractivity
                INNER JOIN tsuser tu ON tsuserid = tu.id
                INNER JOIN spybot_mergeduser mu ON tu.merged_user_id = mu.id
                WHERE starttime > start_of_week.date
                """.trimIndent(),
            )
        return ActiveUsersStat(
            usersThisWeek = record?.get("users_this_week", Int::class.java) ?: 0,
            usersToday = record?.get("users_today", Int::class.java) ?: 0,
        )
    }

    fun weekTrend(): WeekTrendView {
        val record =
            dsl.fetchOne(
                """
                WITH
                    current_week AS (
                        SELECT
                            DATE_TRUNC('week', CURRENT_DATE) AS start_week,
                            DATE_TRUNC('hour', NOW()) - INTERVAL '1 hour' AS end_week
                    ),
                    compare_week AS (
                        SELECT
                            current_week.end_week - INTERVAL '1 WEEK' AS end_week,
                            current_week.start_week - INTERVAL '1 WEEK' AS start_week
                        FROM current_week
                    ),
                    current_week_data AS (
                        SELECT COALESCE(SUM(activity_hours), 0) AS sum
                        FROM hourlyactivity, current_week
                        WHERE hourlyactivity.datetime >= current_week.start_week
                            AND hourlyactivity.datetime <= current_week.end_week
                    ),
                    compare_week_data AS (
                        SELECT COALESCE(SUM(activity_hours), 0) AS sum
                        FROM hourlyactivity, compare_week
                        WHERE hourlyactivity.datetime >= compare_week.start_week
                            AND hourlyactivity.datetime <= compare_week.end_week
                    )
                SELECT
                    current_week_data.sum AS current_week_sum,
                    compare_week_data.sum AS compare_week_sum,
                    CASE
                        WHEN compare_week_data.sum != 0
                            THEN current_week_data.sum / compare_week_data.sum
                        ELSE 0
                    END AS fraction,
                    CASE
                        WHEN current_week_data.sum = 0 AND compare_week_data.sum = 0 THEN 0
                        WHEN compare_week_data.sum = 0 THEN 'Infinity'
                        ELSE 100 * ((current_week_data.sum / compare_week_data.sum) - 1)
                    END AS delta_percent
                FROM current_week_data, compare_week_data
                """.trimIndent(),
            ) ?: return WeekTrendView(0.0, 0.0, 0.0, "0")

        return WeekTrendView(
            currentWeekSum = record.double("current_week_sum"),
            compareWeekSum = record.double("compare_week_sum"),
            fraction = record.double("fraction"),
            deltaPercent = record.get("delta_percent")?.toString() ?: "0",
        )
    }

    fun weekComparison(): List<WeekComparisonPoint> =
        dsl
            .fetch(
                """
                WITH current_week AS (
                        SELECT
                            DATE_TRUNC('week', CURRENT_DATE) AS start,
                            DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '1 week' AS end
                    ),
                compare_week AS (
                    SELECT
                        current_week.end - INTERVAL '1 WEEK' AS end,
                        current_week.start - INTERVAL '1 WEEK' AS start
                    FROM current_week
                ),
                current_week_data AS (
                    SELECT datetime, activity_hours
                    FROM hourlyactivity, current_week
                    WHERE hourlyactivity.datetime >= current_week.start
                        AND hourlyactivity.datetime <= current_week.end
                ),
                compare_week_data AS (
                    SELECT datetime, activity_hours
                    FROM hourlyactivity, compare_week
                    WHERE hourlyactivity.datetime >= compare_week.start
                        AND hourlyactivity.datetime <= compare_week.end
                ),
                cumulate_current_week_data AS (
                    SELECT datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                    FROM current_week_data
                ),
                cumulate_compare_week_data AS (
                    SELECT datetime + INTERVAL '7 DAY' AS datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                    FROM compare_week_data
                )
                SELECT comp.datetime, cur.cumulative_sum AS hours_current, comp.cumulative_sum AS hours_compare
                FROM cumulate_compare_week_data AS comp
                LEFT JOIN cumulate_current_week_data cur ON comp.datetime = cur.datetime
                """.trimIndent(),
            ).map {
                WeekComparisonPoint(
                    datetime = it.offsetDateTime("datetime") ?: OffsetDateTime.now(ZoneOffset.UTC),
                    hoursCurrent = it.get("hours_current")?.let { value -> (value as Number).toDouble() },
                    hoursCompare = it.get("hours_compare")?.let { value -> (value as Number).toDouble() },
                )
            }

    fun channelPopularity(): List<ChannelPopularityEntry> =
        dsl
            .fetch(
                """
                WITH unfiltered AS (
                    SELECT ROUND(SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime)) / 3600)) AS hours,
                        tschannel.name
                    FROM tsuseractivity
                    INNER JOIN tschannel ON tsuseractivity.cid = tschannel.id
                    WHERE starttime > NOW() - INTERVAL '1 YEAR'
                        AND tschannel.name NOT LIKE '%spacer%'
                    GROUP BY tschannel.id
                ), absolute AS (
                    SELECT * FROM unfiltered
                    WHERE hours > 5
                ), total_hours AS (
                    SELECT SUM(hours) AS hours FROM absolute
                )
                SELECT
                    absolute.name,
                    100 * absolute.hours / total_hours.hours AS percentage
                FROM absolute, total_hours
                ORDER BY percentage DESC
                """.trimIndent(),
            ).map {
                ChannelPopularityEntry(
                    name = unescapeTeamSpeak(it.string("name")),
                    percentage = it.double("percentage"),
                )
            }

    fun recentEvents(start: Int): RecentEventsPayload {
        val rows =
            dsl
                .select(
                    SPYBOT_NEWSEVENT.ID,
                    SPYBOT_NEWSEVENT.TEXT,
                    SPYBOT_NEWSEVENT.WEBSITE_LINK,
                    SPYBOT_NEWSEVENT.DATE,
                ).from(SPYBOT_NEWSEVENT)
                .orderBy(SPYBOT_NEWSEVENT.DATE.desc())
                .offset(start)
                .limit(11)
                .fetch()
        val hasMore = rows.size == 11
        val events =
            rows.take(10).map {
                val date = it.offsetDateTime("date") ?: OffsetDateTime.now(ZoneOffset.UTC)
                RecentEventView(
                    id = it.long("id"),
                    text = it.string("text"),
                    websiteLink = it.get("website_link", String::class.java),
                    date = date,
                    isRecent = date.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(1)),
                )
            }
        return RecentEventsPayload(events = events, hasMore = hasMore, start = start + events.size)
    }

    fun hallOfFame(): List<HallOfFameEntry> =
        dsl
            .fetch(
                """
                WITH total_time AS (
                    SELECT
                        spybot_mergeduser.id AS user_id,
                        spybot_mergeduser.name AS user_name,
                        SUM(EXTRACT(EPOCH FROM AGE(COALESCE(tsuseractivity.endtime, NOW()), tsuseractivity.starttime))) AS time
                    FROM tsuseractivity, tsuser, spybot_mergeduser
                    WHERE tsuseractivity.tsuserid = tsuser.id
                    AND spybot_mergeduser.id = tsuser.merged_user_id
                    GROUP BY tsuser.merged_user_id, spybot_mergeduser.name, spybot_mergeduser.id
                    ORDER BY time DESC
                    LIMIT 25
                ),
                awards AS (
                    SELECT
                        merged_user_id,
                        COUNT(*) FILTER (WHERE points = 3) AS gold,
                        COUNT(*) FILTER (WHERE points = 2) AS silver,
                        COUNT(*) FILTER (WHERE points = 1) AS bronze
                    FROM spybot_award
                    GROUP BY merged_user_id
                )
                SELECT
                    total_time.user_id,
                    total_time.user_name AS "user",
                    total_time.time,
                    COALESCE(awards.gold, 0) AS gold,
                    COALESCE(awards.silver, 0) AS silver,
                    COALESCE(awards.bronze, 0) AS bronze
                FROM total_time
                LEFT JOIN awards ON awards.merged_user_id = total_time.user_id
                ORDER BY total_time.time DESC
                """.trimIndent(),
            ).map { row ->
                HallOfFameEntry(
                    userId = row.long("user_id"),
                    user = unescapeTeamSpeak(row.string("user")),
                    time = row.double("time"),
                    numGoldAwards = row.int("gold"),
                    numSilverAwards = row.int("silver"),
                    numBronzeAwards = row.int("bronze"),
                )
            }

    fun timeline(rangeHours: Int): Pair<TimeRangeView, List<TimelineUserSeries>> {
        val allowed = listOf(6, 12, 24)
        val selected = allowed.firstOrNull { it == rangeHours } ?: allowed.first()
        val options = allowed.map { SelectorOption("$it hours", it, it == selected) }
        val cutoff = Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(selected.toLong()).toInstant())
        val rows =
            dsl.fetch(
                """
                select
                    a.starttime,
                    a.endtime,
                    c.name as channel_name,
                    c."order" as channel_order,
                    u.name as user_name
                from tsuseractivity a
                join tschannel c on c.id = a.cid
                join tsuser u on u.id = a.tsuserid
                where a.endtime > ? or a.endtime is null
                order by c."order"
                """.trimIndent(),
                cutoff,
            )

        val users = linkedMapOf<String, MutableList<TimelineEntry>>()
        rows.forEach { row ->
            val start = row.offsetDateTime("starttime") ?: return@forEach
            val end = row.offsetDateTime("endtime") ?: OffsetDateTime.now(ZoneOffset.UTC)
            if (end.toEpochMillis() - start.toEpochMillis() <= 10_000) {
                return@forEach
            }

            val userName = row.string("user_name")
            val channelName = unescapeTeamSpeak(row.string("channel_name"))
            val entry =
                TimelineEntry(
                    x = channelName,
                    y = listOf(start.toEpochMillis(), end.toEpochMillis()),
                )
            users.computeIfAbsent(userName) { mutableListOf() }.add(entry)
        }

        return TimeRangeView(selected, options) to
            users.map { (name, data) ->
                TimelineUserSeries(name = name, data = data)
            }
    }

    fun userPage(userId: Long): UserPageView? {
        val user =
            dsl.fetchOne(
                """
                WITH user_time AS (
                    SELECT
                        tsuseractivity.starttime AS starttime,
                        tsuseractivity.endtime AS endtime,
                        tsuseractivity.cid AS channel,
                        tsuserid AS user_id,
                        tsuser.merged_user_id AS mergeduserid
                    FROM tsuseractivity
                    JOIN tsuser ON tsuseractivity.tsuserid = tsuser.id
                    WHERE tsuser.merged_user_id = ?
                ),
                total_time AS (
                    SELECT
                        SUM(CASE WHEN channel IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 AS afk_time,
                        SUM(CASE WHEN channel NOT IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 AS online_time,
                        MAX(endtime) AS last_seen,
                        MIN(starttime) AS first_seen
                    FROM user_time
                    GROUP BY mergeduserid
                ),
                awards AS (
                    SELECT
                        string_agg(DISTINCT tu.name, ',') AS names,
                        sm.name AS merged_username,
                        bool_or(tu.iscurrentlyonline) AS online,
                        SUM(CASE WHEN points = 1 THEN 1 ELSE 0 END) AS bronze,
                        SUM(CASE WHEN points = 2 THEN 1 ELSE 0 END) AS silver,
                        SUM(CASE WHEN points = 3 THEN 1 ELSE 0 END) AS gold
                    FROM spybot_award
                    RIGHT JOIN tsuser tu ON spybot_award.tsuser_id = tu.id
                    JOIN spybot_mergeduser sm ON tu.merged_user_id = sm.id
                    WHERE tu.merged_user_id = ?
                    GROUP BY tu.merged_user_id, sm.name
                )
                SELECT *
                FROM total_time, awards
                """.trimIndent(),
                userId,
                userId,
            ) ?: return null

        val streak =
            dsl
                .fetchOne(
                    """
                    WITH dates AS (
                        SELECT DISTINCT CAST(tsuseractivity.starttime AS DATE) AS day
                        FROM tsuseractivity
                        INNER JOIN tsuser ON tsuserid = tsuser.id
                        WHERE merged_user_id = ?
                    ),
                    cte AS (
                        SELECT
                            day,
                            COALESCE(DATE(day) > DATE(LAG(day, 1) OVER (ORDER BY day)) + INTERVAL '1 DAY', true) AS startsstreak
                        FROM dates
                    ),
                    result AS (
                        SELECT
                            dates.day AS start_day,
                            SUM(startsstreak::int) AS streakgroup,
                            ROW_NUMBER() OVER (PARTITION BY SUM(startsstreak::int) ORDER BY dates.day) AS runningstreaklength,
                            COUNT(*) OVER (PARTITION BY SUM(startsstreak::int)) AS totalstreaklength
                        FROM dates
                        JOIN cte ON dates.day >= cte.day AND cte.startsstreak = true
                        GROUP BY dates.day
                        ORDER BY dates.day
                    )
                    SELECT start_day,
                           start_day + make_interval(days => totalstreaklength::int - 1) AS end_day,
                           totalstreaklength AS length
                    FROM result
                    WHERE runningstreaklength = 1
                    ORDER BY totalstreaklength DESC, start_day DESC
                    LIMIT 1
                    """.trimIndent(),
                    userId,
                )?.let {
                    StreakView(
                        startDay = it.localDate("start_day") ?: LocalDate.now(),
                        endDay = it.localDate("end_day") ?: LocalDate.now(),
                        length = it.int("length"),
                    )
                }

        val months =
            dsl
                .fetch(
                    """
                    WITH data AS (
                        SELECT
                            DATE_PART('year', starttime) AS year,
                            DATE_PART('month', starttime) AS month,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        INNER JOIN tsuser ON tsuseractivity.tsuserid = tsuser.id
                        WHERE starttime > MAKE_DATE(2016, 1, 1)
                            AND endtime IS NOT NULL
                            AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                            AND tsuser.merged_user_id = ?
                        GROUP BY year, month
                        ORDER BY year, month
                    ),
                    months AS (
                        WITH RECURSIVE nrows(date) AS (
                            SELECT MAKE_DATE(2016, 1, 1)::timestamptz
                            UNION ALL
                            SELECT date + INTERVAL '1 MONTH' FROM nrows WHERE date <= CURRENT_DATE - INTERVAL '1 MONTH'
                        )
                        SELECT date FROM nrows
                    )
                    SELECT DATE_PART('month', months.date) AS month,
                           DATE_PART('year', months.date) AS year,
                           COALESCE(data.time_hours, 0) AS activity
                    FROM months
                    LEFT JOIN data
                        ON DATE_PART('year', months.date) = data.year
                       AND DATE_PART('month', months.date) = data.month
                    ORDER BY year, month
                    """.trimIndent(),
                    userId,
                ).map {
                    MonthActivityPoint(
                        month = it.int("month"),
                        year = it.int("year"),
                        activity = it.double("activity"),
                    )
                }

        val headline =
            UserHeadline(
                names = user.string("names").split(',').filter { it.isNotBlank() },
                mergedUsername = user.string("merged_username"),
                online = user.boolean("online"),
                bronze = user.int("bronze"),
                silver = user.int("silver"),
                gold = user.int("gold"),
                afkTime = user.double("afk_time"),
                onlineTime = user.double("online_time"),
                lastSeen = user.offsetDateTime("last_seen"),
                firstSeen = user.offsetDateTime("first_seen"),
            )

        return UserPageView(
            userId = userId,
            headline = headline,
            streak = streak,
            bests = recordsQueries.personalBests(userId),
            months = months,
            totalTime = (headline.afkTime + headline.onlineTime).roundToInt(),
            gameId = 0,
            gameName = "",
        )
    }

    fun recordHourlyActivity() {
        dsl.execute(
            """
            INSERT INTO hourlyactivity(datetime, activity_hours)
            WITH startofhour AS (
                SELECT DATE_TRUNC('hour', NOW()) AS stamp
            ),
            activityhours AS (
                SELECT CAST(COALESCE(SUM(
                    EXTRACT(EPOCH FROM AGE(
                        COALESCE(endtime, NOW()),
                        CASE WHEN startofhour.stamp > starttime THEN startofhour.stamp ELSE starttime END
                    ))
                ), 0) AS FLOAT) / 3600 AS activity_hours
                FROM tsuseractivity, startofhour
                WHERE endtime IS NULL OR endtime > startofhour.stamp
            )
            SELECT startofhour.stamp, activityhours.activity_hours
            FROM startofhour, activityhours
            """.trimIndent(),
        )
    }

    fun weeklyAwardCandidates(): List<TopUserWeek> = topUsersOfWeek()
}
