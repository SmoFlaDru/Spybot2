# Generated by Django 4.1.4 on 2023-01-09 19:47

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('spybot', '0007_alter_tsuser_id_queuedclientmessage_award_and_more'),
    ]

    operations = [
        migrations.AlterField(
            model_name='award',
            name='date',
            field=models.DateTimeField(auto_now_add=True),
        ),
    ]