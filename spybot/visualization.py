from typing import TypedDict, List

from django.db import connection


def dictfetchall(cursor):
    columns = [col[0] for col in cursor.description]
    return [
        dict(zip(columns, row))
        for row in cursor.fetchall()
    ]


def daily_activity():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH active_data AS (
                SELECT
                    DATE_FORMAT(CAST(startTime AS date), '%Y-%m-%d') AS date,
                    SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600 AS time_hours
                FROM TSUserActivity
                INNER JOIN TSChannel channel on TSUserActivity.cID = channel.id
                WHERE
                    startTime > DATE_SUB(CURDATE(), INTERVAL 1 MONTH)
                    AND endTime IS NOT NULL
                    AND channel.name NOT IN ('bei\\\sBedarf\\\sanstupsen', 'AFK')
                GROUP BY date
                ORDER BY date
            ),
            afk_data AS (
                SELECT
                    DATE_FORMAT(CAST(startTime AS date), '%Y-%m-%d') AS date,
                    SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600 AS time_hours
                FROM TSUserActivity
                INNER JOIN TSChannel channel on TSUserActivity.cID = channel.id
                WHERE
                    startTime > DATE_SUB(CURDATE(), INTERVAL 1 MONTH)
                    AND endTime IS NOT NULL
                    AND channel.name IN ('bei\\\sBedarf\\\sanstupsen', 'AFK')
                GROUP BY date
                ORDER BY date
            )
            SELECT active_data.date, 
                CAST(active_data.time_hours AS DOUBLE) AS active_hours, 
                COALESCE(CAST(afk_data.time_hours AS DOUBLE), 0) AS afk_hours
            FROM active_data
            LEFT OUTER JOIN afk_data ON active_data.date = afk_data.date;
        """)
        return cursor.fetchall()


def time_of_day_histogram():
    with connection.cursor() as cursor:
        cursor.execute("""
            SELECT DATE_FORMAT(datetime, '%H') as hour, AVG(activity_hours) AS amplitude
            FROM HourlyActivity
            GROUP BY hour;
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
                SELECT DATE_ADD(UTC_DATE(), INTERVAL(-WEEKDAY(UTC_DATE())) DAY) AS date
            )
            SELECT
                SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600 AS time,
                MU.name AS user_name,
                MU.id AS user_id
            FROM startOfWeek, TSUserActivity
            INNER JOIN TSUser TU ON tsUserID = TU.id
            INNER JOIN spybot_mergeduser MU ON TU.merged_user_id = MU.id
            WHERE startTime > startOfWeek.date
                AND endTime IS NOT NULL
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
                        CAST(DATE_ADD(UTC_DATE(), INTERVAL(-WEEKDAY(UTC_DATE())) DAY) AS datetime) AS start,
                        DATE_ADD(DATE_ADD(DATE_ADD(
                            UTC_TIMESTAMP(), INTERVAL(-MINUTE(UTC_TIMESTAMP())) MINUTE),
                            INTERVAL(-SECOND(UTC_TIMESTAMP())) SECOND),
                            INTERVAL(-1) HOUR) AS end
                ),
                compareWeek AS (
                    SELECT
                        DATE_ADD(currentWeek.end, INTERVAL -1 WEEK) AS end,
                        DATE_ADD(currentWeek.start, INTERVAL -1 WEEK) AS start
                    FROM currentWeek
                ),
                currentWeekData AS (
                    SELECT COALESCE(SUM(activity_hours), 0) AS sum
                    FROM HourlyActivity, currentWeek
                    WHERE HourlyActivity.datetime >= currentWeek.start
                        AND HourlyActivity.datetime <= currentWeek.end
                ),
                compareWeekData AS (
                    SELECT COALESCE(SUM(activity_hours), 0) AS sum
                    FROM HourlyActivity, compareWeek
                    WHERE HourlyActivity.datetime >= compareWeek.start
                        AND HourlyActivity.datetime <= compareWeek.end
                )
            SELECT currentWeekData.sum AS current_week_sum, 
                compareWeekData.sum AS compare_week_sum,
                currentWeekData.sum / compareWeekData.sum AS fraction,
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
                    temp.start AS start,
                    CAST(DATE_ADD(temp.start, INTERVAL 7 DAY) AS datetime) as end
                FROM (
                    SELECT CAST(DATE_ADD(UTC_DATE(), INTERVAL(-WEEKDAY(UTC_DATE())) DAY) AS datetime) AS start
                ) temp
            ),
            compareWeek AS (
                SELECT
                    DATE_ADD(currentWeek.end, INTERVAL -1 WEEK) AS end,
                    DATE_ADD(currentWeek.start, INTERVAL -1 WEEK) AS start
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
                SELECT datetime + INTERVAL 7 DAY AS datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
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
            WITH absolute AS (
                SELECT ROUND(SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600) AS hours,
                    TSChannel.name
                FROM TSUserActivity
                INNER JOIN TSChannel on TSUserActivity.cID = TSChannel.id
                WHERE startTime > DATE_ADD(UTC_TIMESTAMP(), INTERVAL -1 YEAR)
                    AND TSChannel.name NOT LIKE '%spacer%'
                GROUP BY TSChannel.id
                HAVING hours > 5
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
                    spybot_mergeduser.name as user,
                    SUM(TIMESTAMPDIFF(SECOND, TSUserActivity.startTime, COALESCE(TSUserActivity.endTime, UTC_TIMESTAMP()))) as time
                FROM TSUserActivity, TSUser, spybot_mergeduser
                WHERE TSUserActivity.tsUserID = TSUser.id
               AND spybot_mergeduser.id = TSUser.merged_user_id
               GROUP BY TSUser.merged_user_id
            )
            SELECT
                user,
                time,
                BIG_SEC_TO_TIME(time) AS formatted_time
            FROM total_time
            ORDER BY time DESC;
        """)
        return dictfetchall(cursor)

