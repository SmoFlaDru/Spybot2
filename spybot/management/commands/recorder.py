from django.core.management import BaseCommand, CommandError


class Command(BaseCommand):
    def handle(self, *args, **options):
        try:
            from spybot.recorder.recorder import Recorder
            from Spybot2 import settings
            if settings.RECORDER_ENABLED:
                print('Starting recorder')
                rec = Recorder()
                rec.run()
        except Exception as e:
            raise CommandError(f'Error in recorder command: {e}')
