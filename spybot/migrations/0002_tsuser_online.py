# Generated by Django 4.1.3 on 2022-12-04 23:33

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('spybot', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='tsuser',
            name='online',
            field=models.BooleanField(db_column='isCurrentlyOnline', default=False),
        ),
    ]
