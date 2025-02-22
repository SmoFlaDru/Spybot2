# Generated by Django 5.1.6 on 2025-02-20 17:47

from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("spybot", "0002_alter_loginlink_code"),
    ]

    operations = [
        migrations.RenameIndex(
            model_name="hourlyactivity",
            new_name="hourlyactiv_datetim_4aadb3_idx",
            old_name="HourlyActiv_datetim_96f0af_idx",
        ),
        migrations.RenameIndex(
            model_name="tsuseractivity",
            new_name="tsuseractiv_startti_ebb367_idx",
            old_name="TSUserActiv_startTi_95ea75_idx",
        ),
        migrations.AlterField(
            model_name="tschannel",
            name="id",
            field=models.AutoField(primary_key=True, serialize=False),
        ),
        migrations.AlterField(
            model_name="tsuseractivity",
            name="id",
            field=models.AutoField(primary_key=True, serialize=False),
        ),
    ]
