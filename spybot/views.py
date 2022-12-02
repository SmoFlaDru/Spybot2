from django.http import HttpResponse
from django.template import loader

from spybot.models import TSChannel


def index(request):
    return HttpResponse("Spybot is here")


def home(request):
    channel_list = TSChannel.objects.all()
    template = loader.get_template('home.html')
    context = { 'channel_list': channel_list }
    return HttpResponse(template.render(context, request))