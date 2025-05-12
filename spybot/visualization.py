from typing import TypedDict, List

from django.db import connection


def dictfetchall(cursor):
    columns = [col[0] for col in cursor.description]
    return [dict(zip(columns, row)) for row in cursor.fetchall()]


def daily_activity(days: int):
    date_format = "%Y-%m-%d"

    with connection.cursor() as cursor:
        cursor.execute(
            r"""
            WITH active_data AS (
                SELECT
                    TO_CHAR(starttime, 'YYYY-MM-DD') AS date,
                    SUM(EXTRACT(EPOCH FROM AGE(endTime, startTime))) / 3600 AS time_hours
                FROM TSUserActivity
                INNER JOIN TSChannel channel on TSUserActivity.cID = channel.id
                WHERE
                    startTime > CURRENT_DATE - INTERVAL '%(days)s days'
                    AND endTime IS NOT NULL
                    AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                GROUP BY date
                ORDER BY date
            ),
            afk_data AS (
                SELECT
                    TO_CHAR(startTime, 'YYYY-MM-DD') AS date,
                    SUM(EXTRACT(EPOCH FROM AGE(endTime, startTime))) / 3600 AS time_hours
                FROM TSUserActivity
                INNER JOIN TSChannel channel on TSUserActivity.cID = channel.id
                WHERE
                    startTime > CURRENT_DATE - INTERVAL '%(days)s days'
                    AND endTime IS NOT NULL
                    AND channel.name IN ('bei\sBedarf\sanstupsen', 'AFK')
                GROUP BY date
                ORDER BY date
            )
            SELECT active_data.date, 
                CAST(active_data.time_hours AS DOUBLE PRECISION) AS active_hours, 
                COALESCE(CAST(afk_data.time_hours AS DOUBLE PRECISION), 0) AS afk_hours
            FROM active_data
            LEFT OUTER JOIN afk_data ON active_data.date = afk_data.date;
        """,
            {"days": days, "date_format": date_format},
        )
        return cursor.fetchall()


def time_of_day_histogram():
    with connection.cursor() as cursor:
        cursor.execute("""
            SELECT TO_CHAR(datetime, 'HH24') AS hour, AVG(activity_hours) AS amplitude
            FROM HourlyActivity
            GROUP BY hour
            ORDER BY hour;
        """)
        return cursor.fetchall()


class TopUserResult(TypedDict):
    time: float
    user_name: str
    user_id: int


def top_users_of_week() -> List[TopUserResult]:
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH startOfWeek AS (
                SELECT DATE_TRUNC('week', CURRENT_DATE)::DATE AS date
            )
            SELECT
                SUM(EXTRACT(EPOCH FROM AGE(COALESCE(endTime, NOW()), startTime))) / 3600 AS time,
                MU.name AS user_name,
                MU.id AS user_id
            FROM startOfWeek, TSUserActivity
            INNER JOIN TSUser TU ON tsUserID = TU.id
            INNER JOIN spybot_mergeduser MU ON TU.merged_user_id = MU.id
            WHERE startTime > startOfWeek.date
            GROUP BY MU.id
            ORDER BY time DESC
            LIMIT 3;
        """)
        return dictfetchall(cursor)


def week_activity_trend():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH
                currentWeek AS (
                    SELECT
                        DATE_TRUNC('week', CURRENT_DATE) AS startWeek,
                        DATE_TRUNC('hour', NOW()) - INTERVAL '1 hour' endWeek
                ),
                compareWeek AS (
                    SELECT
                        currentWeek.endWeek - INTERVAL '1 WEEK' AS endWeek,
                        currentWeek.startWeek - INTERVAL '1 WEEK' AS startWeek
                    FROM currentWeek
                ),
                currentWeekData AS (
                    SELECT COALESCE(SUM(activity_hours), 0) AS sum
                    FROM HourlyActivity, currentWeek
                    WHERE HourlyActivity.datetime >= currentWeek.startWeek
                        AND HourlyActivity.datetime <= currentWeek.endWeek
                ),
                compareWeekData AS (
                    SELECT COALESCE(SUM(activity_hours), 0) AS sum
                    FROM HourlyActivity, compareWeek
                    WHERE HourlyActivity.datetime >= compareWeek.startWeek
                        AND HourlyActivity.datetime <= compareWeek.endWeek
                )
            SELECT
                currentWeekData.sum AS current_week_sum,
                compareWeekData.sum AS compare_week_sum,
                CASE
                    WHEN compareWeekData.sum != 0
                        THEN currentWeekData.sum / compareWeekData.sum
                        ELSE 0
                    END AS fraction,
            CASE
                WHEN currentWeekData.sum = 0 AND compareWeekData.sum = 0 THEN 0
                WHEN compareWeekData.sum = 0 THEN 'infinity'
                ELSE 100 * ((currentWeekData.sum / compareWeekData.sum) - 1)
            END AS delta_percent
            FROM currentWeekData, compareWeekData;
        """)
        return dictfetchall(cursor)


def week_activity_comparison():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH currentWeek AS (
                    SELECT
                        DATE_TRUNC('week', CURRENT_DATE) AS start,
                        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '1 week' AS end
                ),
            compareWeek AS (
                SELECT
                    currentWeek.end - INTERVAL '1 WEEK' AS end,
                    currentWeek.start - INTERVAL '1 WEEK' AS start
                FROM currentWeek
            ),
            currentWeekData AS (
                SELECT datetime, activity_hours
                FROM HourlyActivity, currentWeek
                WHERE HourlyActivity.datetime >= currentWeek.start
                    AND HourlyActivity.datetime <= currentWeek.end
            ),
            compareWeekData AS (
                SELECT datetime, activity_hours
                FROM HourlyActivity, compareWeek
                WHERE HourlyActivity.datetime >= compareWeek.start
                    AND HourlyActivity.datetime <= compareWeek.end
            ),
            cumulateCurrentWeekData AS (
                SELECT datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                FROM currentWeekData
            ),
            cumulateCompareWeekData AS (
                SELECT datetime + INTERVAL '7 DAY' AS datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                FROM compareWeekData
            )
            SELECT comp.datetime, cur.cumulative_sum AS hours_current, comp.cumulative_sum AS hours_compare
            FROM cumulateCompareWeekData AS comp
            LEFT JOIN cumulateCurrentWeekData cur ON comp.datetime = cur.datetime;
        """)
        return dictfetchall(cursor)


def channel_popularity():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH unfiltered AS (
                SELECT ROUND(SUM(EXTRACT(EPOCH FROM AGE(endTime, startTime)) / 3600)) AS hours,
                    TSChannel.name
                FROM TSUserActivity
                INNER JOIN TSChannel on TSUserActivity.cID = TSChannel.id
                WHERE startTime > NOW() - INTERVAL '1 YEAR'
                    AND TSChannel.name NOT LIKE '%spacer%'
                GROUP BY TSChannel.id
            ), absolute AS (
                SELECT * FROM unfiltered
                WHERE hours > 5
            ), total_hours AS (
                SELECT SUM(hours) as hours FROM absolute
            )
            SELECT
                absolute.name,
                100 * absolute.hours / total_hours.hours AS percentage
            FROM absolute, total_hours
            ORDER BY percentage DESC;
        """)
        return dictfetchall(cursor)


def user_hall_of_fame():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH total_time AS (
                SELECT
                    spybot_mergeduser.id as user_id,
                    spybot_mergeduser.name as user_name,
                    SUM(EXTRACT(EPOCH FROM AGE(COALESCE(TSUserActivity.endTime, NOW()), TSUserActivity.startTime))) as time
                FROM TSUserActivity, TSUser, spybot_mergeduser
                WHERE TSUserActivity.tsUserID = TSUser.id
                AND spybot_mergeduser.id = TSUser.merged_user_id
                GROUP BY TSUser.merged_user_id, spybot_mergeduser.name, spybot_mergeduser.id
            )
            SELECT
                user_id,
                user_name AS "user",
                time
            FROM total_time
            ORDER BY time DESC
            LIMIT 25;
        """)

        return dictfetchall(cursor)


def user(user_id: int):
    with connection.cursor() as cursor:
        cursor.execute(
            """
            WITH user_time AS (
                SELECT
                    TSUserActivity.startTime as starttime,
                    TSUserActivity.endTime as endtime,
                    TSUserActivity.cID as channel,
                    tsUserID as user_ID,
                    TSUser.merged_user_id AS mergeduserid
                FROM TSUserActivity JOIN TSUser on TSUserActivity.tsUserID = TSUser.id
                WHERE TSUser.merged_user_id = %s
            ), total_time AS (
                SELECT
                    SUM(CASE WHEN channel IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 as afk_time,
                    SUM(CASE WHEN channel NOT IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 as online_time,
                    MAX(endtime) as last_seen,
                    MIN(starttime) as first_seen
                FROM user_time
                GROUP BY mergeduserid
            ), awards as (
                SELECT
                    string_agg(DISTINCT TU.name, ',') as names,
                    sm.name as merged_username,
                    bool_or(TU.isCurrentlyOnline) as online,
                    SUM(CASE WHEN points=1 THEN 1 ELSE 0 END) as bronze,
                    SUM(CASE WHEN points=2 THEN 1 ELSE 0 END) as silver,
                    SUM(CASE WHEN points=3 THEN 1 ELSE 0 END) as gold
                FROM spybot_award RIGHT JOIN TSUser TU on spybot_award.tsuser_id = TU.id
                JOIN spybot_mergeduser sm on TU.merged_user_id = sm.id
                WHERE TU.merged_user_id = %s
                GROUP BY TU.merged_user_id, sm.name
            )
            SELECT *
            FROM total_time, awards;""",
            [user_id, user_id],
        )

        return dictfetchall(cursor)


def user_longest_streak(merged_user_id: int):
    with connection.cursor() as cursor:
        cursor.execute(
            """
            WITH dates AS (
                SELECT DISTINCT
                    spybot_mergeduser.name,
                    CAST(TSUserActivity.startTime AS DATE) AS day
                FROM TSUserActivity
                    INNER JOIN TSUser ON tsUserID = TSUser.id
                    INNER JOIN spybot_mergeduser ON TSUser.merged_user_id = spybot_mergeduser.id
                WHERE merged_user_id = %s
            ),
            cte AS (
                SELECT
                    day,
                    COALESCE(DATE(day) > DATE(LAG(day, 1) OVER (ORDER BY day)) + INTERVAL '1 DAY', true) AS startsStreak
                FROM dates
            ),
            result AS (
                SELECT
                    dates.day AS start_day,
                    SUM(startsStreak::int) AS streakGroup,
                    ROW_NUMBER() OVER (PARTITION BY SUM(startsStreak::int) ORDER BY dates.day) AS runningStreakLength,
                    COUNT(*) OVER (PARTITION BY SUM(startsStreak::int)) AS totalStreakLength
                FROM
                    dates
                    JOIN cte ON dates.day >= cte.day AND cte.startsStreak = true
                GROUP BY dates.day
                ORDER BY dates.day
            )
            SELECT
            start_day,
            start_day + make_interval(days => totalStreakLength::int - 1) AS end_day,
            totalStreakLength AS length
            FROM result
            WHERE runningStreakLength = 1
            ORDER BY totalStreakLength DESC, start_day DESC
            LIMIT 1;
        """,
            [merged_user_id],
        )
        return dictfetchall(cursor)


def user_month_activity(merged_user_id: int):
    with connection.cursor() as cursor:
        cursor.execute(
            r"""
        WITH data AS (
            SELECT
                DATE_PART('year', startTime) AS year,
                DATE_PART('month', startTime) AS month,
                SUM(EXTRACT(EPOCH FROM AGE(endTime, startTime))) / 3600 AS time_hours
            FROM TSUserActivity
                       INNER JOIN TSChannel channel on TSUserActivity.cID = channel.id
            INNER JOIN TSUser ON TSUserActivity.tsUserID = TSUSER.id
            WHERE startTime > MAKE_DATE(2016, 1, 1)
                AND endTime IS NOT NULL
                AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                AND TSUser.merged_user_id = %s
            GROUP BY year, month
            ORDER BY year, month),
        months AS (
            WITH RECURSIVE nrows(date) AS (
                SELECT MAKE_DATE(2016, 1, 1)::timestamptz UNION ALL
                SELECT date + INTERVAL '1 MONTH' FROM nrows WHERE date<= CURRENT_DATE - INTERVAL '1 MONTH'
            )
            SELECT date FROM nrows
        )
        SELECT DATE_PART('month', months.date) AS month,
               DATE_PART('year', months.date) AS year,
               COALESCE(data.time_hours, 0) AS activity
        FROM months
        LEFT JOIN data ON DATE_PART('year', months.date) = data.year AND DATE_PART('month', months.date) = data.month
        ORDER BY year, month;
        """,
            [merged_user_id],
        )
        return dictfetchall(cursor)


#
# WITH dates AS (
#     SELECT DISTINCT
#         CAST(TSUserActivity.startTime AS DATE) AS day
#     FROM TSUserActivity
#     ORDER BY day DESC
# ),
# cte AS (
#     SELECT
#         day,
#         IFNULL(DATE(day) > DATE(LAG(day, 1) OVER (ORDER BY day)) + INTERVAL 1 DAY, 1) AS startsStreak
#     FROM dates
# ),
# result AS (
#     SELECT
#         dates.day AS start_day,
#         SUM(startsStreak) AS streakGroup,
#         ROW_NUMBER() OVER (PARTITION BY SUM(startsStreak) ORDER BY dates.day) AS runningStreakLength,
#         COUNT(*) OVER (PARTITION BY SUM(startsStreak)) AS totalStreakLength
#     FROM
#         dates
#         JOIN cte ON dates.day >= cte.day AND cte.startsStreak = 1
#     GROUP BY dates.day
#     ORDER BY dates.day
# )
# SELECT
# start_day,
# DATE_ADD(start_day, INTERVAL (totalStreakLength - 1) DAY) AS end_day,
# totalStreakLength AS streak_length
# FROM result
# WHERE runningStreakLength = 1
# ORDER BY totalStreakLength DESC, start_day DESC
# LIMIT 1;
