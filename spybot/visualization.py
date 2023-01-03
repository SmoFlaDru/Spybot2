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
                TRIM(TRAILING '1' FROM TU.name) AS user_name,
                TU.id AS user_id
            FROM startOfWeek, TSUserActivity
            INNER JOIN TSUser TU on tsUserID = TU.id
            WHERE startTime > startOfWeek.date
                AND endTime IS NOT NULL
            GROUP BY user_name
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
                CASE compareWeekData.sum
                    WHEN 0 THEN 'infinity'
                    ELSE 100 * ((currentWeekData.sum / compareWeekData.sum) - 1) 
                END AS delta_percent
            FROM currentWeekData, compareWeekData;
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
