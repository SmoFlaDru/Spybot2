# This is an auto-generated Django model module.
# You'll have to do the following manually to clean this up:
#   * Rearrange models' order
#   * Make sure each model has one field with primary_key=True
#   * Make sure each ForeignKey and OneToOneField has `on_delete` set to the desired behavior
#   * Remove `managed = False` lines if you wish to allow Django to create, modify, and delete the table
# Feel free to rename the models, but don't rename db_table values or field names.
from django.db import models
from django.db.models.fields import AutoFieldMixin, PositiveIntegerField, AutoField
from django.utils import timezone


class DebuggableModel(models.Model):
    class Meta:
        abstract = True

    def __repr__(self):
        fields = self._meta.get_fields()
        buf = "<%s" % self.__class__.__name__

        for field in fields:
            if not field.concrete:
                continue

            buf += " %s=%s" % (field.name, getattr(self, field.name))

        buf += ">"

        return buf

    def __str__(self):
        return self.__repr__()


class TSChannel(DebuggableModel):
    id = models.PositiveIntegerField(primary_key=True, null=False)
    name = models.CharField(max_length=64, blank=True, null=True)
    order = models.PositiveIntegerField(null=False)

    class Meta:
        managed = True
        db_table = 'TSChannel'


# Django doesn't have an unsigned int auto (auto-incremented) field type natively, so we provide our own
class PositiveAutoField(AutoFieldMixin, PositiveIntegerField):
    def get_internal_type(self):
        return "PositiveIntegerField"

    def rel_db_type(self, connection):
        return PositiveIntegerField().db_type(connection=connection)


class MergedUser(DebuggableModel):
    name = models.CharField(max_length=128, blank=False, null=False)
    obsolete = models.BooleanField(default=False)

    def merged_user_names(self):
        return list(TSUser.objects.values_list('name', flat=True).filter(merged_user=self))


class SteamID(DebuggableModel):
    steam_id = models.BigIntegerField(default=0)
    account_name = models.CharField(max_length=128, blank=True, null=True)
    merged_user = models.ForeignKey(MergedUser, on_delete=models.CASCADE, related_name="steamids")


class TSUser(DebuggableModel):
    id = AutoField(primary_key=True)
    name = models.CharField(max_length=128, blank=True, null=True)
    client_id = models.PositiveIntegerField(db_column="clientID")
    # maybe remove
    online = models.BooleanField(db_column='isCurrentlyOnline', default=False)
    merged_user = models.ForeignKey(MergedUser, on_delete=models.SET_NULL, null=True, related_name="tsusers")

    class Meta:
        managed = True
        db_table = 'TSUser'

    def last_login_time(self):
        return getattr(TSUserActivity.objects.filter(tsuser=self, end_time__isnull=False).order_by('-end_time').first(), 'end_time', None)


class TSID(DebuggableModel):
    tsuser = models.ForeignKey(TSUser, models.DO_NOTHING, db_column='tsUserID', blank=True, null=True)
    ts_id = models.CharField(db_column='tsID', max_length=32, primary_key=True)

    class Meta:
        managed = True
        db_table = 'TSID'


class TSUserActivity(DebuggableModel):
    id = models.IntegerField(primary_key=True, null=False)
    tsuser = models.ForeignKey(TSUser, models.DO_NOTHING, db_column='tsUserID', blank=True, null=True)  # Field name made lowercase.
    start_time = models.DateTimeField(db_column='startTime', blank=True, null=True)  # Field name made lowercase.
    end_time = models.DateTimeField(db_column='endTime', blank=True, null=True)  # Field name made lowercase.
    joined = models.BooleanField(null=False, default=False)
    disconnect_id = models.IntegerField(db_column='discID', blank=True, null=True)  # Field name made lowercase.
    channel = models.ForeignKey(TSChannel, models.DO_NOTHING, db_column='cID')  # Field name made lowercase.

    class Meta:
        managed = True
        db_table = 'TSUserActivity'
        indexes = [
            models.Index(fields=['start_time'])
        ]


class HourlyActivity(DebuggableModel):
    datetime = models.DateTimeField(null=False, default=timezone.now)
    activity_hours = models.FloatField(null=False)

    class Meta:
        db_table = 'HourlyActivity'
        indexes = [
            models.Index(fields=['datetime']),
        ]


class QueuedClientMessage(DebuggableModel):
    tsuser = models.ForeignKey(TSUser, models.CASCADE, blank=False, null=False)
    text = models.CharField(max_length=1024, blank=False, null=False)
    type = models.CharField(max_length=128, blank=False, null=False)
    date = models.DateField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=['tsuser', 'type'], name='constraint_unique_type_user')
        ]


class Award(DebuggableModel):
    class AwardType(models.TextChoices):
        USER_OF_WEEK = 'USER_OF_WEEK', 'User of the week'

    tsuser = models.ForeignKey(TSUser, models.CASCADE, blank=False, null=False, related_name="awards")
    date = models.DateTimeField(auto_now_add=True)
    type = models.CharField(max_length=64, choices=AwardType.choices, default=AwardType.USER_OF_WEEK, null=False)
    points = models.IntegerField(blank=False, null=False)


class NewsEvent(DebuggableModel):
    text = models.CharField(max_length=1024, null=False)
    website_link = models.CharField(max_length=256, null=True)
    date = models.DateTimeField(auto_now_add=True)
