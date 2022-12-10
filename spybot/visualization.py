from django.db import connection


def daily_activity():
    with connection.cursor() as cursor:
        cursor.execute("""
            WITH data AS (
                SELECT
                    DATE_FORMAT(CAST(startTime AS date), '%Y-%m-%d') AS date,
                    SUM(TIMESTAMPDIFF(SECOND, startTime, endTime)) / 3600 AS time_hours
                FROM TSUserActivity
                WHERE
                    startTime > DATE_SUB(CURDATE(), INTERVAL 1 MONTH)
                    AND endTime IS NOT NULL
                GROUP BY date
            )
            SELECT date, CAST(time_hours AS DOUBLE)
            FROM data
            WHERE date != '2022-11-30'
            ORDER BY date
        """)
        return cursor.fetchall()

