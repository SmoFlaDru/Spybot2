from datetime import datetime, timezone, timedelta

from django.test import TestCase

from spybot.models import HourlyActivity
from spybot.views.views import week_trend_tile


class ViewsTestCase(TestCase):
    def setUp(self):
        pass

    def test_week_trend_tile_infinite_growth(self):
        # given
        timestamp = datetime.now(tz=timezone.utc) - timedelta(hours=3)
        current_week_activity = HourlyActivity(datetime=timestamp, activity_hours=1)
        current_week_activity.save()

        # when
        result = week_trend_tile()

        # then
        self.assertEqual(
            result,
            {
                "week_trend": {
                    "current_week_sum": 1.0,
                    "compare_week_sum": 0.0,
                    "fraction": 0.0,
                    "delta_percent": float("inf"),
                },
                "week_comparison": [],
            },
        )
