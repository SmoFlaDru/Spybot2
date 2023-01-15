from django.contrib import admin
from django.contrib.admin import ModelAdmin

from spybot.models import NewsEvent


# Register your models here.
@admin.register(NewsEvent)
class NewsEventAdmin(ModelAdmin):
    list_display = ('text', 'website_link', 'date')
