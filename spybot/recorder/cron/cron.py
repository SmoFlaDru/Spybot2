from datetime import timedelta, datetime

from django.db import connection
from num2words import num2words

from Spybot2 import settings
from spybot import visualization
from spybot.models import TSUser, Award, QueuedClientMessage, NewsEvent


def end_of_week_awards():
    print("Calculating weekly awards")
    top_users = visualization.top_users_of_week()
    print(top_users)
    # reverse list to save third award, then second, then first award
    top_users.reverse()

    for idx, result in enumerate(top_users):

        user = TSUser.objects.get(id=result['user_id'])
        # correct index because of reversed list
        idx = 2 - idx

        score = 3 - idx

        # create award
        award = Award(tsuser=user, type=Award.AwardType.USER_OF_WEEK, points=score)
        award.save()

        # create news event
        _generate_news_event_for_top_user_of_week(user, idx, score)

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

        # remove last message if it exists
        previous_message = QueuedClientMessage.objects.filter(tsuser=user, type="AWARD_USER_OF_WEEK")
        if previous_message.exists():
            previous_message.first().delete()

        queued_message = QueuedClientMessage(tsuser=user, text=message, type="AWARD_USER_OF_WEEK")
        queued_message.save()


def _generate_news_event_for_top_user_of_week(user: TSUser, idx: int, points: int):
    date = datetime.now()
    week_of_year = int(date.strftime("%V"))
    year = date.year

    # match by name to account for duplicate accounts of the same person
    previous_awards_count = Award.objects.filter(tsuser__name=user.name).count()
    previous_same_score_awards_count = Award.objects.filter(tsuser__name=user.name, points=points).count()

    # create link
    link = f"/u/{user.id}"

    # create first message line
    specifier = ""
    metal = "gold"
    if idx == 1:
        specifier = " second"
        metal = "silver"
    elif idx == 2:
        specifier = " third"
        metal = "bronze"

    # create second message line
    previous_times_specifier, metal_type_specifier = "", ""
    end = "."

    match previous_awards_count, previous_same_score_awards_count:
        case 1, _:
            previous_times_specifier = "the first time"
            metal_type_specifier = "any"
        case _, 1:
            previous_times_specifier = f"the first time"
            metal_type_specifier = f"a {metal}"
        case _, nth if nth < 4:
            num = num2words(nth, to='ordinal')
            previous_times_specifier = f"only the {num} time"
            metal_type_specifier = f"a {metal}"
        case overall, nth:
            num = num2words(nth, to='ordinal')
            previous_times_specifier = f"the {num} time"
            metal_type_specifier = f"a {metal}"
            num_overall = num2words(overall, to='ordinal')
            end = f", {num_overall} award overall."

    second_line = f"This is {previous_times_specifier} {user.name} won {metal_type_specifier} award{end}"

    message = f"{user.name} earned the {metal} award for being the{specifier} most active user of week&nbsp;{week_of_year} " \
              f"in {year}. Congratulations! {second_line}"

    n = NewsEvent(text=message, website_link=link)
    n.save()


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
