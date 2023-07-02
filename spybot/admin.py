from typing import List, Set
from django.contrib import admin, messages
from django.contrib.admin import ModelAdmin
from django.db.models import QuerySet
from django.http import HttpRequest

from spybot.models import NewsEvent, TSUser, MergedUser


# Register your models here.
@admin.register(NewsEvent)
class NewsEventAdmin(ModelAdmin):
    list_display = ('text', 'website_link', 'date')


@admin.action(description="Merge selected users")
def merge_users_action(admin: ModelAdmin, request: HttpRequest, queryset: QuerySet):
    users: List[TSUser] = list(queryset)
    if len(users) < 2:
        messages.error(request, 'Need at least two users to merge')
        return

    print("Merging users", users)
    first: TSUser = users[0]
    new_merged_head = first.merged_user
    other_ts_users: Set[TSUser] = set()

    for other_user in users[1:]:
        other_ts_users.update(set(other_user.merged_user.tsusers.all()))

    print("Other ts users: ", other_ts_users)
    for user in other_ts_users:
        old_merged_user = user.merged_user
        if old_merged_user != new_merged_head:
            print("Merging user and obsoleting merged user", user, old_merged_user)
            user.merged_user = new_merged_head
            old_merged_user.obsolete = True
            old_merged_user.save()
            user.save()



@admin.register(TSUser)
class TSUserAdmin(ModelAdmin):
    actions = [merge_users_action]
    list_display = ('id', 'name', 'merged_user', 'last_login_time')
    list_max_show_all = 2000
    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(MergedUser)
class MergedUserAdmin(ModelAdmin):
    list_display = ('id', 'name', 'merged_user_names', 'obsolete')
    def has_delete_permission(self, request, obj=None):
        return False
