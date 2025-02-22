import os

from django.apps import AppConfig


class SpybotConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "spybot"

    def ready(self):
        if os.environ.get("RUN_MAIN"):
            print("spybot app ready")
