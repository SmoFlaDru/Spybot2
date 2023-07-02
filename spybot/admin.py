from django.contrib import admin
from django.contrib.admin import ModelAdmin

from spybot.models import NewsEvent, TSUser, MergedUser


# Register your models here.
@admin.register(NewsEvent)
class NewsEventAdmin(ModelAdmin):
    list_display = ('text', 'website_link', 'date')


@admin.register(TSUser)
class TSUserAdmin(ModelAdmin):
    pass


@admin.register(MergedUser)
class MergedUserAdmin(ModelAdmin):
    def has_delete_permission(self, request, obj=None):
        return False
