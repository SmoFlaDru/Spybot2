from django.test import TestCase, override_settings

from spybot.models import TSUserActivity, TSUser, MergedUser

@override_settings(DEBUG=False)
class TSUserTestCase(TestCase):
    def setUp(self) -> None:
        u = TSUser(name="Otto Normal", client_id=42, online=False)
        u.save()

    def test_user(self):
        user = TSUser.objects.get(name="Otto Normal")
        self.assertEqual(user.client_id, 42)