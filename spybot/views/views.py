import datetime
import time
from datetime import timedelta
from typing import List

from django.contrib.auth.decorators import login_required
from django.db.models import Q
from django.http import HttpResponse, HttpResponseForbidden
from django.shortcuts import get_object_or_404, render
from django.utils import timezone

from spybot import visualization
from spybot.forms import AddSteamIDForm, TimeRangeForm
from spybot.models import (
    MergedUser,
    NewsEvent,
    SteamID,
    TSChannel,
    TSUserActivity,
    UserPasskey,
)
from spybot.remote.steam_api import get_steam_user_playing_info
from spybot.templatetags import ts_filters
from spybot.views.common import get_context, get_user
from spybot.views.fragments.activity_chart import activity_chart_data
from spybot.views.fragments.profile_steamids import profile_steamids_data


def get_passkeys(user: MergedUser) -> List[UserPasskey]:
    return UserPasskey.objects.filter(user=user).all()


def home(request):
    context = get_context(request)

    # logged in user
    user = get_user(request)
    context["user"] = user

    # activity chart
    context = {**context, **activity_chart_data(request)}

    # time of day histogram
    tod_data = visualization.time_of_day_histogram()
    context['tod_data'] = list(tod_data)

    # top users of week tile
    top_users = visualization.top_users_of_week()
    context['top_users_data'] = list(top_users)

    # week trend tile
    week_trend = visualization.week_activity_trend()
    week_comparison = visualization.week_activity_comparison()
    # convert to float if it's not the special infinity value
    if week_trend[0]['delta_percent'] != 'infinity':
        week_trend[0]['delta_percent'] = round(float(week_trend[0]['delta_percent']))
    context['week_trend'] = week_trend[0]
    context['week_comparison'] = week_comparison

    # channel popularity
    channel_popularity = visualization.channel_popularity()
    for row in channel_popularity:
        row["name"] = ts_filters.replace_ts_special_chars(row["name"])
    context["channel_data"] = channel_popularity

    # recent events
    context["recent_events"] = get_recent_events()

    return render(request, 'spybot/home/home.html', context)


def get_recent_events(start=0):
    query_result = NewsEvent.objects.order_by("-date")[start:start + 11].values()
    has_more = len(query_result) == 11
    events = query_result[:10]

    for event in events:
        is_recent = timezone.now() - event["date"] < datetime.timedelta(weeks=1)
        event["is_recent"] = is_recent
    return {
        'events': events,
        'has_more': has_more,
        'start': start + len(events)
    }


# TODO load live view separately and poll with javascript
def live(request):
    channels = TSChannel.objects.order_by('order')
    sessions = TSUserActivity.objects.filter(end_time=None)

    clients = []

    for session in sessions:
        channel_id = session.channel.id
        user_name = session.tsuser.name
        clients.append({'channel_id': channel_id, 'name': user_name})

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})




def timeline(request):
    form = TimeRangeForm(request.GET or {})
    form.is_valid()
    time_hours = int(form.cleaned_data.get('range'))

    cutoff = timezone.now() - timedelta(hours=time_hours)
    now_time = timezone.now()
    data = TSUserActivity.objects.filter(
        Q(end_time__gt=cutoff) | Q(end_time__isnull=True)
    ).order_by('channel__order')

    def convert_to_jstime(dt: datetime.datetime):
        # JS dates are in unix timestamp * 1000
        return time.mktime(dt.timetuple()) * 1000

    already_seen_channels = set()

    users = {}
    for x in data:
        # skip activities shorter than 10 seconds
        if x.end_time is not None and (x.end_time - x.start_time).total_seconds() <= 10:
            continue

        user_name = x.tsuser.name
        if user_name not in users:
            users[user_name] = {
                'name': user_name,
                'data': []
            }

        if x.end_time is None:
            x.end_time = timezone.now()

        users[user_name]['data'].append({
            'x': ts_filters.replace_ts_special_chars(x.channel.name),
            'y': [
                convert_to_jstime(x.start_time),
                convert_to_jstime(x.end_time),
            ]
        })

        if x.channel.name not in already_seen_channels:
            already_seen_channels.add(x.channel.name)

    # fixes for correct channel order
    if len(users) > 0:
        first_user = next(iter(users))
        first_user_object = users[first_user]
        first_user_series = first_user_object["data"]
        new_first_user_series = []

        for c in TSChannel.objects.order_by('order'):
            if c.name in already_seen_channels:
                # make sure this channel is in the first user series
                # check if it already exists, copy it over
                items = list(data_point for data_point in first_user_series
                             if data_point['x'] == ts_filters.replace_ts_special_chars(c.name))
                if len(items) > 0:
                    new_first_user_series += items
                else:
                    # insert dummy entry for channel ordering purposes
                    new_first_user_series.append({
                        'x': ts_filters.replace_ts_special_chars(c.name),
                        'y': []
                    })
        first_user_object["data"] = new_first_user_series

    return render(request, 'spybot/timeline.html', {
        **get_context(request),
        'activity_by_user': list(users.values()),
        'min': convert_to_jstime(cutoff),
        'max': convert_to_jstime(now_time),
        'form': form,
    })


def halloffame(request):
    users = visualization.user_hall_of_fame()

    for merged_user in users:
        awards_gold = []
        awards_silver = []
        awards_bronze = []
        mu = MergedUser.objects.get(id=merged_user['user_id'])
        for u in mu.tsusers.all():
            for a in u.awards.all():
                if a.points == 3:
                    awards_gold.append(a)
                elif a.points == 2:
                    awards_silver.append(a)
                elif a.points == 1:
                    awards_bronze.append(a)
        merged_user['num_gold_awards'] = len(awards_gold)
        merged_user['num_silver_awards'] = len(awards_silver)
        merged_user['num_bronze_awards'] = len(awards_bronze)

    context = {**get_context(request), 'top_users': users}
    return render(request, 'spybot/halloffame.html', context)


@login_required()
def user(request, user_id: int):
    # name, first_online, is_online | last_online, aliase, total_time, afk_time

    u = visualization.user(user_id)[0]
    afk_time = int(u.get('afk_time'))
    online_time = int(u.get('online_time'))

    streak = visualization.user_longest_streak(user_id)[0]

    game_name = ""
    game_id = 0

    if u.get('online') == 1:
        game_id, game_name = get_steam_game(MergedUser.objects.get(id=user_id))

    months = visualization.user_month_activity(user_id)

    return render(request, 'spybot/user.html', {
        **get_context(request),
        'user': u,
        'total_time': afk_time + online_time,
        'data': [afk_time, online_time],
        'names': str(u.get('names')).split(","),
        'game_id': game_id,
        'game_name': game_name,
        'streak': streak,
        'months': months,
    })


def live_fragment(request):
    # live view
    sessions = TSUserActivity.objects.filter(end_time=None)
    channels = TSChannel.objects.order_by('order')
    clients = []

    for session in sessions:
        channel_id = session.channel.id
        user_name = session.tsuser.name
        merged_user_id = session.tsuser.merged_user_id

        mu = MergedUser.objects.get(id=merged_user_id)
        game_id, game_name = get_steam_game(mu)
        clients.append({'channel_id': channel_id, 'name': user_name,
                        'merged_user_id': merged_user_id, 'game': game_name})

    context = {'clients': clients, 'channels': channels}
    return render(request, 'spybot/home/live_fragment.html', context)


def get_steam_game(mu: MergedUser):
    steam_ids = mu.steamids.all()

    if not steam_ids:
        return 0, ""

    for sid in steam_ids:
        steam_id = sid.steam_id
        game_id, game_name = get_steam_user_playing_info(steam_id)
        if game_id is not None:
            return game_id, game_name
    return 0, ""


def recent_events_fragment(request):
    start_string = request.GET.get('start', '')
    start = int(start_string) if start_string.isdecimal() else 0

    data = get_recent_events(start)

    return render(request, 'spybot/home/recent_events/fragment.html', {'data': data})


def profile(request):
    user = get_user(request)
    passkeys = get_passkeys(user)
    steamids = profile_steamids_data(request)
    return render(request, 'spybot/profile/profile.html', {**steamids, **get_context(request), 'user': user, 'passkeys': passkeys})

def login(request):
    user = get_user(request)
    return render(request, 'spybot/login.html', {**get_context(request), 'user': user})


def login_teamspeak(request):
    user = get_user(request)
    return render(request, 'spybot/login_teamspeak.html', {**get_context(request), 'user': user})


@login_required
def profile_passkey(request, id: str):
    if request.method == "DELETE":
        print(f"trying to delete passkey with id {id}")
        passkey = get_object_or_404(UserPasskey, id=id)
        if passkey.user != request.user:
            return HttpResponseForbidden()

        passkey.delete()
        return HttpResponse('')
    return None


def create_steamid(user: MergedUser, steam_id: str, account_name:str):
    sid = SteamID(steam_id=int(steam_id), account_name=account_name, merged_user=user)
    sid.save()


@login_required
def profile_add_steamid(request):
    user = get_user(request)
    if request.method == "POST":
        form = AddSteamIDForm(request.POST)
        if form.is_valid():
            create_steamid(user, form.cleaned_data['steamid'], form.cleaned_data['name'])
            return HttpResponse(status=204, headers={'HX-Trigger': 'steamids_changed'})
    else:
        form = AddSteamIDForm()
    return render(request,
                  'spybot/profile/add_steamid_modal.html',
                  {**get_context(request), 'user': user, 'form': form}
                  )


def profile_delete_steamid(request, id):
    if request.method == "DELETE":
        print(f"trying to delete steamid with id {id}")
        steamid = get_object_or_404(SteamID, id=id)
        if steamid.merged_user != request.user:
            return HttpResponseForbidden()

        steamid.delete()
        return HttpResponse(status=200, headers={'HX-Trigger': 'steamids_changed'})
    return None


def profile_steamids_fragment(request):
    user = get_user(request)
    steamids = profile_steamids_data(request)
    return render(request, 'spybot/profile/steamids_fragment.html', {**steamids, **get_context(request), 'user': user})
