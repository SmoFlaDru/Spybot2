from django.http import HttpResponse, JsonResponse
from django.shortcuts import render
from django.template import loader

from spybot.models import TSChannel, TSUser, TSUserActivity
from spybot.recorder.ts import TS


def helloworld(request):
    template = loader.get_template('spybot/helloworld.html')
    return HttpResponse(template.render({}, request))


def home(request):
    channel_list = TSChannel.objects.all()
    context = {'channel_list': channel_list}
    return render(request, 'spybot/home.html', context)


def live_legacy(request):
    ts = TS()
    ts.make_conn()
    clients = ts.get_clients()

    channels = TSChannel.objects.order_by('order')

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})


def live(request):
    channels = TSChannel.objects.order_by('order')
    clients = TSUser.objects.filter(online=True)

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})


def spybot(request):
    return render(request, 'spybot/base/sidebar.html')

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
