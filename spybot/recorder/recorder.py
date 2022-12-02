import time
from threading import Thread
import ts3
from django.conf import settings

from spybot.models import TSChannel


class Recorder:

    def start(self):
        thread = Thread(target=self.run, daemon=True)
        thread.start()

    def run(self):
        ts_user = settings.TS_USER
        ts_password = settings.TS_PASSWORD
        ts_ip = settings.TS_IP
        ts_port = settings.TS_PORT

        while True:
            with ts3.query.TS3ServerConnection(f"telnet://{ts_user}:{ts_password}@{ts_ip}:{ts_port}") as ts3conn:
                ts3conn.exec_("use", sid=1)

                # Register for events
                ts3conn.exec_("servernotifyregister", event="channel", id=0)

                while True:
                    event = ts3conn.wait_for_event()
                    print(f"new Event: {event.parsed}")


                """print("hello from background thread")
                # test database and print results
                channels = TSChannel.objects.order_by('name').all()
                output = ' '.join(c.name for c in channels)
                print(output)
                time.sleep(5)"""

            print("Error, restarting connection")
