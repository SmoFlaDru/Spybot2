# Generated by Django 4.1.3 on 2022-12-04 22:47

from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    initial = True

    dependencies = [
    ]

    operations = [
        migrations.CreateModel(
            name='TSChannel',
            fields=[
                ('id', models.PositiveIntegerField(primary_key=True, serialize=False)),
                ('name', models.CharField(blank=True, max_length=64, null=True)),
                ('order', models.PositiveIntegerField()),
            ],
            options={
                'db_table': 'TSChannel',
                'managed': True,
            },
        ),
        migrations.CreateModel(
            name='TSUser',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(blank=True, max_length=128, null=True)),
                ('client_id', models.PositiveIntegerField(db_column='clientID')),
            ],
            options={
                'db_table': 'TSUser',
                'managed': True,
            },
        ),
        migrations.CreateModel(
            name='TSUserActivity',
            fields=[
                ('id', models.IntegerField(primary_key=True, serialize=False)),
                ('start_time', models.DateTimeField(blank=True, db_column='startTime', null=True)),
                ('end_time', models.DateTimeField(blank=True, db_column='endTime', null=True)),
                ('joined', models.BooleanField(default=False)),
                ('disconnect_id', models.IntegerField(blank=True, db_column='discID', null=True)),
                ('channel_id', models.ForeignKey(db_column='cID', on_delete=django.db.models.deletion.DO_NOTHING, to='spybot.tschannel')),
                ('tsuser_id', models.ForeignKey(blank=True, db_column='tsUserID', null=True, on_delete=django.db.models.deletion.DO_NOTHING, to='spybot.tsuser')),
            ],
            options={
                'db_table': 'TSUserActivity',
                'managed': True,
            },
        ),
        migrations.CreateModel(
            name='TSID',
            fields=[
                ('ts_id', models.CharField(db_column='tsID', max_length=32, primary_key=True, serialize=False)),
                ('tsuser', models.ForeignKey(blank=True, db_column='tsUserID', null=True, on_delete=django.db.models.deletion.DO_NOTHING, to='spybot.tsuser')),
            ],
            options={
                'db_table': 'TSID',
                'managed': True,
            },
        ),
    ]
