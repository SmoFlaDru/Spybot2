from django.http import HttpResponse
from django.shortcuts import render
from django.conf import settings
from django.template import loader

from spybot.models import TSChannel

import ts3


def index(request):
    return HttpResponse("Spybot is here")


def home(request):
    channel_list = TSChannel.objects.all()
    template = loader.get_template('home.html')
    context = { 'channel_list': channel_list }
    return HttpResponse(template.render(context, request))


def live(request):
    ts_user = settings.TS_USER
    ts_password = settings.TS_PASSWORD
    ts_ip = settings.TS_IP
    ts_port = settings.TS_PORT

    channels = TSChannel.objects.all()

    with ts3.query.TS3ServerConnection(f"telnet://{ts_user}:{ts_password}@{ts_ip}:{ts_port}") as ts3conn:
        ts3conn.exec_("use", sid=1)
        resp = ts3conn.exec_("clientlist")
        clients = resp.parsed

        return render(request, 'spybot/live.html', {'clients': clients, 'channels': channels})
