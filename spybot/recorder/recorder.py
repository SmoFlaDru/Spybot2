import time
from threading import Thread

from spybot.models import TSChannel


class Recorder():

    def start(self):
        thread = Thread(target=self.run, daemon=True)
        thread.start()

    def run(self):
        while True:
            print("hello from background thread")
            # test database and print results
            channels = TSChannel.objects.order_by('name').all()
            output = ' '.join(c.name for c in channels)
            print(output)
            time.sleep(5)
