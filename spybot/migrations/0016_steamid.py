# Generated by Django 4.2.3 on 2023-07-16 20:41

from django.db import migrations, models
import django.db.models.deletion


def migrate_old_steam_ids(apps, schema_editor):
    MergedUser = apps.get_model("spybot", "MergedUser")
    SteamID = apps.get_model("spybot", "SteamID")

    for mu in MergedUser.objects.all():
        sid = mu.steam_id
        if sid != 0:
            steam_id = SteamID(merged_user=mu, steam_id=sid)
            steam_id.save()
            mu.steam_id = 0
            mu.save()
            print(f"Migrated old steam_id {sid} of user {mu.id}")


class Migration(migrations.Migration):

    dependencies = [
        ('spybot', '0015_alter_mergeduser_steam_id'),
    ]

    operations = [
        migrations.CreateModel(
            name='SteamID',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('steam_id', models.BigIntegerField(default=0)),
                ('merged_user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='steamids', to='spybot.mergeduser')),
            ],
            options={
                'abstract': False,
            },
        ),

        migrations.RunPython(migrate_old_steam_ids)
    ]