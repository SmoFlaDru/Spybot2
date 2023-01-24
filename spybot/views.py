import datetime
import time
from datetime import timedelta

from django.db.models import Q
from django.http import HttpResponse, JsonResponse
from django.shortcuts import render
from django.template import loader
from django.utils import timezone

from spybot import visualization
from spybot.forms import TimeRangeForm
from spybot.models import TSChannel, TSUserActivity, NewsEvent
from spybot.templatetags import ts_filters


def helloworld(request):
    template = loader.get_template('spybot/helloworld.html')
    return HttpResponse(template.render({}, request))


def home(request):
    # activity chart
    data = visualization.daily_activity()
    dates, active_values, afk_values = zip(*data)
    # convert tuples back to lists when passing to template
    context = {'daily_dates': list(dates),
               'daily_active_values': list(active_values),
               'daily_afk_values': list(afk_values)
               }

    # live view
    sessions = TSUserActivity.objects.filter(end_time=None)
    channels = TSChannel.objects.order_by('order')
    clients = []

    for session in sessions:
        channel_id = session.channel.id
        user_name = session.tsuser.name
        clients.append({'channel_id': channel_id, 'name': user_name})
    context['clients'] = clients
    context['channels'] = channels

    # time of day histogram
    tod_data = visualization.time_of_day_histogram()
    context['tod_data'] = list(tod_data)

    # top users of week tile
    top_users = visualization.top_users_of_week()
    context['top_users_data'] = list(top_users)

    # week trend tile
    week_trend = visualization.week_activity_trend()
    # convert to float if it's not the special infinity value
    week_trend[0]['delta_percent'] = -0.3
    if week_trend[0]['delta_percent'] != 'infinity':
        week_trend[0]['delta_percent'] = round(float(week_trend[0]['delta_percent']))
    context['week_trend'] = week_trend[0]

    # channel popularity
    channel_popularity = visualization.channel_popularity()
    for row in channel_popularity:
        row["name"] = ts_filters.replace_ts_special_chars(row["name"])
    context["channel_data"] = channel_popularity

    # recent events
    recent_events = NewsEvent.objects.order_by("-date")[:10].values()
    for event in recent_events:
        is_recent = timezone.now() - event["date"] < datetime.timedelta(weeks=1)
        event["is_recent"] = is_recent
    context["recent_events"] = recent_events

    return render(request, 'spybot/home/home.html', context)


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


def spybot(request):
    return render(request, 'spybot/base/navbar.html')


def widget_legacy(request):
    sessions = TSUserActivity.objects.filter(end_time=None)

    active_clients = []
    inactive_clients = []
    res = {}
    for session in sessions:
        user_name = session.tsuser_id.name
        channel_name = session.channel_id.name
        if channel_name == "bei Bedarf anstupsen" or channel_name == "AFK":
            inactive_clients.append(user_name)
        else:
            active_clients.append(user_name)
    res["activeClients"] = active_clients
    res["inactiveClients"] = inactive_clients
    return JsonResponse(res)


def timeline(request):
    form = TimeRangeForm(request.POST or {})
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
        'activity_by_user': list(users.values()),
        'min': convert_to_jstime(cutoff),
        'max': convert_to_jstime(now_time),
        'form': form,
    })
