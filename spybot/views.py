import time
from datetime import timedelta

from django.http import HttpResponse, JsonResponse
from django.shortcuts import render
from django.template import loader
from django.utils import timezone

from spybot import visualization
from spybot.models import TSChannel, TSUser, TSUserActivity
from spybot.recorder.ts import TS


def helloworld(request):
    template = loader.get_template('spybot/helloworld.html')
    return HttpResponse(template.render({}, request))


def home(request):
    # activity chart
    data = visualization.daily_activity()
    dates, active_values, afk_values = zip(*data)
    # convert tuples back to lists when passing to template
    context = {'dates': list(dates), 'active_values': list(active_values), 'afk_values': list(afk_values)}

    # live view
    sessions = TSUserActivity.objects.filter(end_time=None)
    channels = TSChannel.objects.order_by('order')
    clients = []

    for session in sessions:
        channel_id = session.channel.id
        user_name = session.tsuser.name
        print(f"appending client {user_name}")
        clients.append({'channel_id': channel_id, 'name': user_name})
    context['clients'] = clients
    context['channels'] = channels
    print(f"context: {context}")

    return render(request, 'spybot/home/home.html', context)


# TODO load live view seperately and poll with javascript
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
        print(f"session={session}")
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
    cutoff = timezone.now() - timedelta(hours=6)
    data = TSUserActivity.objects.filter(start_time__gte=cutoff).order_by('channel__order')

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
                # JS dates are in unix timestamp * 1000
                time.mktime(x.start_time.timetuple()) * 1000,
                time.mktime(x.end_time.timetuple()) * 1000
            ]
        })

        if x.channel.name not in already_seen_channels:
            already_seen_channels.add(x.channel.name)

    # fixes for correct channel order
    first_user = next(iter(users))
    first_user_object = users[first_user]
    first_user_series = first_user_object["data"]
    new_first_user_series = []

    for c in TSChannel.objects.order_by('order'):
        if c.name in already_seen_channels:
            # make sure this channel is in the first user series
            # check if it already exists, copy it over
            series = next((s for s in first_user_series if s['x'] == ts_filters.replace_ts_special_chars(c.name)), None)
            if series is not None:
                new_first_user_series.append(series)
            else:
                # insert dummy entry for channel ordering purposes
                new_first_user_series.append({
                    'x': ts_filters.replace_ts_special_chars(c.name),
                    'y': []
                })
    first_user_object["data"] = new_first_user_series

    return render(request, 'spybot/timeline.html', {'activity_by_user': list(users.values())})
