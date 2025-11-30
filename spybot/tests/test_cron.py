from unittest.mock import patch

from django.test import TestCase
from django.db import connection

from Spybot2 import settings
from spybot.recorder.cron.cron import end_of_week_awards
from spybot.models import MergedUser, TSUser, Award, QueuedClientMessage, NewsEvent


class EndOfWeekAwardsTestCase(TestCase):
    def setUp(self):
        # create 6 merged users, each with two distinct TSUsers
        self.merged_users = []
        for i in range(6):
            mu = MergedUser.objects.create(name=f"MergedUser{i + 1}")
            # create one TSUser with id equal to merged user id to accommodate cron's queued message FK usage
            TSUser.objects.create(
                id=mu.id,
                name=f"TSUser-primary-{i + 1}",
                client_id=100 + i,
                merged_user=mu,
            )
            # create a second TSUser with an explicit id to avoid sequence conflicts
            TSUser.objects.create(
                id=1000 + mu.id,
                name=f"TSUser-extra-{i + 1}",
                client_id=200 + i,
                merged_user=mu,
            )
            self.merged_users.append(mu)

        # reset the tsuser id sequence to a value higher than any manually inserted id
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT setval(pg_get_serial_sequence('tsuser', 'id'), (SELECT COALESCE(MAX(id), 1) + 1 FROM tsuser), false);"
            )

    @patch("spybot.visualization.top_users_of_week")
    def test_simple_awards_six_users_top_three_get_awards(self, mock_top_users_of_week):
        # Ensure SERVER_IP is defined for message URL formatting
        settings.SERVER_IP = "testserver.local"

        # Prepare top users: first three merged users are top; ordered from best to worst
        top_three = [
            {
                "time": 3000.0,
                "user_name": self.merged_users[0].name,
                "user_id": self.merged_users[0].id,
            },
            {
                "time": 2000.0,
                "user_name": self.merged_users[1].name,
                "user_id": self.merged_users[1].id,
            },
            {
                "time": 1000.0,
                "user_name": self.merged_users[2].name,
                "user_id": self.merged_users[2].id,
            },
        ]
        mock_top_users_of_week.return_value = top_three

        # Call the function under test
        end_of_week_awards()

        # Check that exactly 3 awards were created for these merged users
        awards = Award.objects.filter(type=Award.AwardType.USER_OF_WEEK).order_by(
            "-points"
        )
        self.assertEqual(awards.count(), 3)

        # Best user should have 3 points, second 2, third 1
        points_per_user = {award.merged_user.id: award.points for award in awards}
        self.assertEqual(points_per_user[self.merged_users[0].id], 3)
        self.assertEqual(points_per_user[self.merged_users[1].id], 2)
        self.assertEqual(points_per_user[self.merged_users[2].id], 1)

        # Check that queued messages exist for those users.
        # Due to the cron implementation, queued messages are created with tsuser set to the merged user id.
        # We created a TSUser whose id equals the merged user id, so messages should be associated with that TSUser.
        for idx, mu in enumerate(self.merged_users[:3]):
            # There should be exactly one queued message per winning user
            qms = QueuedClientMessage.objects.filter(
                merged_user_id=mu.id, type="AWARD_USER_OF_WEEK"
            )
            self.assertTrue(
                qms.exists(), f"QueuedClientMessage for merged user {mu.id} not found"
            )
            qm = qms.first()
            # message text should contain metal type
            if idx == 0:
                self.assertIn("gold", qm.text)
            elif idx == 1:
                self.assertIn("silver", qm.text)
            elif idx == 2:
                self.assertIn("bronze", qm.text)

        # NewsEvents should have been created for each award
        nevents = NewsEvent.objects.all()
        self.assertEqual(nevents.count(), 3)
        # Ensure news event text contains the user's name
        names_in_events = " ".join(n.text for n in nevents)
        for mu in self.merged_users[:3]:
            self.assertIn(mu.name, names_in_events)
