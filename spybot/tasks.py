from celery import shared_task

from spybot.recorder.cron import cron


@shared_task()
def end_of_week_awards():
    cron.end_of_week_awards()


@shared_task()
def record_hourly_activity():
    cron.record_hourly_activity()
