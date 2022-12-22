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


def top_users_of_week():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH startOfWeek AS (
                SELECT DATE_ADD(UTC_DATE(), INTERVAL(-WEEKDAY(UTC_DATE())) DAY) AS date
            )
            SELECT
                SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600 AS time_hours,
                TU.name AS name,
                TU.id AS userID
            FROM startOfWeek, TSUserActivity
            INNER JOIN TSUser TU on tsUserID = TU.id
            WHERE startTime > startOfWeek.date
                AND endTime IS NOT NULL
            GROUP BY userId
            ORDER BY time_hours DESC
            LIMIT 3;
        """)
        return dictfetchall(cursor)
