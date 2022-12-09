from django.http import HttpResponse
from django.shortcuts import render
from django.template import loader

from spybot.models import TSChannel, TSUser

from spybot.recorder.ts import TS


def helloworld(request):
    template = loader.get_template('spybot/helloworld.html')
    return HttpResponse(template.render({}, request))


def home(request):
    channel_list = TSChannel.objects.all()
    template = loader.get_template('spybot/home.html')
    context = {'channel_list': channel_list}
    return HttpResponse(template.render(context, request))


def live(request):
    ts = TS()
    ts.make_conn()
    clients = ts.get_clients()

    channels = TSChannel.objects.order_by('order')

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})


def live_db(request):
    channels = TSChannel.objects.order_by('order')
    clients = TSUser.objects.filter(online=True)

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})


def spybot(request):
    return render(request, 'spybot/sidebar.html')
