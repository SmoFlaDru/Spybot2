from django.http import HttpResponse
from django.shortcuts import render
from django.template import loader

from spybot.models import TSChannel

from spybot.recorder.ts import TS


def index(request):
    return HttpResponse("Spybot is here")


def home(request):
    channel_list = TSChannel.objects.all()
    template = loader.get_template('home.html')
    context = {'channel_list': channel_list}
    return HttpResponse(template.render(context, request))


def live(request):
    ts = TS()
    ts.make_conn()
    clients = ts.get_clients()

    channels = TSChannel.objects.order_by('order')

    return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})
