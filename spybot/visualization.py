from django.db import connection


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
                    AND channel.name NOT IN ('bei\\sBedarf\\sanstupsen', 'AFK')
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
                    AND channel.name IN ('bei\\sBedarf\\sanstupsen', 'AFK')
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

