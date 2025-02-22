from django.http.request import HttpRequest
from django.http.response import JsonResponse

from spybot.schemas import LiveResponse, TSChannelSchema, TSUserSchema

from .models import TSChannel, TSUserActivity


def widget_legacy(request: HttpRequest) -> JsonResponse:
    sessions = TSUserActivity.objects.filter(end_time=None)

    active_clients: list[str] = []
    inactive_clients: list[str] = []
    for session in sessions:
        user_name = session.tsuser.name
        channel_name = session.channel.name
        if channel_name == "bei Bedarf anstupsen" or channel_name == "AFK":
            inactive_clients.append(user_name)
        else:
            active_clients.append(user_name)

    res = {"activeClients": active_clients, "inactiveClients": inactive_clients}
    return JsonResponse(res)


def live_api(request: HttpRequest) -> JsonResponse:
    channels: list[TSChannelSchema] = [
        {"id": c.id, "name": c.name} for c in TSChannel.objects.order_by("order")
    ]
    sessions = TSUserActivity.objects.filter(end_time=None)

    clients: list[TSUserSchema] = [
        {"channel_id": s.channel.id, "name": s.tsuser.name} for s in sessions
    ]

    res: LiveResponse = {"clients": clients, "channels": channels}
    return JsonResponse(res)


def something(request: HttpRequest) -> JsonResponse:
    return JsonResponse({"status": "Up and running"})
