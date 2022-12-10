from django.http import HttpResponse
from django.shortcuts import render
from django.template import loader

from spybot import visualization
from spybot.models import TSChannel, TSUser
from spybot.recorder.ts import TS


def helloworld(request):
    template = loader.get_template('spybot/helloworld.html')
    return HttpResponse(template.render({}, request))


def home(request):
    data = visualization.daily_activity()
    dates, values = zip(*data)
    # convert tuples back to lists when passing to template
    context = {'dates': list(dates), 'values': list(values)}
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
