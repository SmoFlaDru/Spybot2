from celery import shared_task


@shared_task()
def end_of_week_awards():
    print("shared week awards job")

@shared_task()
def record_hourly_activity():
    print("record hourly activity job")