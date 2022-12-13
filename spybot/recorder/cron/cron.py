import datetime

from django.db import connection


def my_scheduled_job():
    print("Job is running at", datetime.datetime.now())
    pass


def record_hourly_activity():
    print("Collecting hourly activity")

    with connection.cursor() as cursor:
        cursor.execute("""
            INSERT INTO HourlyActivity(datetime, activity_hours)
            WITH startOfHour AS (
                SELECT UTC_TIMESTAMP - INTERVAL second(UTC_TIMESTAMP) SECOND - INTERVAL minute(UTC_TIMESTAMP) MINUTE AS stamp
            )
            SELECT startOfHour.stamp AS datetime,
                CAST(COALESCE(SUM(
                    TIMESTAMPDIFF(SECOND,
                        IF(startOfHour.stamp > startTime, startOfHour.stamp, startTime),
                        COALESCE(endTime, UTC_TIMESTAMP)
                    )
                ), 0) AS FLOAT) / 3600 AS activity_hours
            FROM TSUserActivity, startOfHour
            WHERE endTime IS NULL
                OR endTime > startOfHour.stamp;
        """)
    print("Done collecting activity")
