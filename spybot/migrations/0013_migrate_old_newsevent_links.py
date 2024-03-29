# Generated by Django 4.1.7 on 2023-07-04 16:29
import re

from django.db import migrations


def migrate_old_news_events(apps, schema_editor):
    NewsEvent = apps.get_model("spybot", "NewsEvent")
    TSUser = apps.get_model("spybot", "TSUser")

    old_link_pattern = re.compile(r'/u/(\d+)\Z')
    for news_event in NewsEvent.objects.all():
        old_link = news_event.website_link
        match = old_link_pattern.match(old_link)
        if match:
            old_id = int(match.group(1))
            tsuser = TSUser.objects.get(id=old_id)
            new_id = tsuser.merged_user_id
            new_link = f"/u/{new_id}"
            if new_link != old_link:
                news_event.website_link = new_link
                news_event.save()
                print(f"Migrated old link {old_link} to {new_link}")


class Migration(migrations.Migration):

    dependencies = [
        ('spybot', '0012_mergeduser_tsuser_merged_user'),
    ]

    operations = [
        migrations.RunPython(migrate_old_news_events)
    ]
