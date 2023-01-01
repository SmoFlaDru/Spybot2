from datetime import timedelta, datetime

from django.db import connection

from Spybot2 import settings
from spybot import visualization
from spybot.models import TSUser, Award, QueuedClientMessage


def end_of_week_awards():
    print("Calculating weekly awards")
    top_users = visualization.top_users_of_week()
    print(top_users)
    for idx, result in enumerate(top_users):
        if idx > 2:
            break

        # create award
        score = 3 - idx
        user = TSUser.objects.get(id=result['user_id'])
        award = Award(tsuser=user, type=Award.AwardType.USER_OF_WEEK, points=score)
        award.save()

        # create message
        specifier = ""
        metal = "gold"
        if idx == 1:
            specifier = " second"
            metal = "silver"
        elif idx == 2:
            specifier = " third"
            metal = "bronze"

        week_start = datetime.today() - timedelta(days=datetime.today().weekday() % 7)
        week_string = week_start.strftime("%d.%m.%Y")
        url = "https://" + settings.SERVER_IP
        message = f"You got a {metal} award for being the{specifier} most active user of the week {week_string}! See " \
                  f"more: [url]{url}[/url]"
        queued_message = QueuedClientMessage(tsuser=user, text=message, type="AWARD_USER_OF_WEEK")
        queued_message.save()


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
