import json

from django.http.request import HttpRequest
from django.test import TestCase

from spybot.api import live_api, widget_legacy
from spybot.models import TSChannel, TSUser, TSUserActivity


class ApiTestCase(TestCase):
    def setUp(self):
        user_a = TSUser.objects.create(name="userA", client_id=1, online=False)
        user_b = TSUser.objects.create(name="userB", client_id=2, online=False)

        channel_a = TSChannel.objects.create(name="channelA", order=1)
        channel_b = TSChannel.objects.create(name="AFK", order=2)

        TSUserActivity.objects.create(tsuser=user_a, channel=channel_a, end_time=None)
        TSUserActivity.objects.create(tsuser=user_b, channel=channel_b, end_time=None)

    def test_legacy_widget(self):
        result = widget_legacy(HttpRequest())
        data = json.loads(result.content)

        self.assertEqual(data["activeClients"], ["userA"])
        assert data["inactiveClients"] == ["userB"]

    def test_live_view(self):
        result = live_api(HttpRequest())
        data = json.loads(result.content)

        self.assertEqual(
            data,
            {
                "clients": [
                    {"channel_id": 3, "name": "userA"},
                    {"channel_id": 4, "name": "userB"},
                ],
                "channels": [{"id": 3, "name": "channelA"}, {"id": 4, "name": "AFK"}],
            },
        )
