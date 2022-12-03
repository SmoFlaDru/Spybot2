import traceback
from threading import Thread

from ts3 import TS3Error
from ts3.response import TS3ParserError

from spybot.recorder.client import Client
from spybot.recorder.ts import TS


class Recorder:

    def __init__(self):
        self.client = Client()
        self.ts = TS()

    def start(self):
        thread = Thread(target=self.run, daemon=True)
        thread.start()

    def run(self):

        while True:

            self.ts.make_conn()
            self.ts.register_events()
            self.ts.set_nickname("spytwo")

            self.main_loop()

            # prepare for reconnection attempt
            print("Ultimate Error, restarting connection")

    def main_loop(self):
        try:
            # TODO fetch all channels and update in DB if necessary

            # fetch all connected clients initally
            client_list_response = self.ts.get_clients()

            len(client_list_response)
            print(f"Client-list inital: {client_list_response}")

            for user in client_list_response:
                # enter in db
                self.client.client_enter(
                    client_id=user["clid"],
                    channel_id=user["cid"],
                    client_database_id=user["client_database_id"],
                    client_nickname=user["client_nickname"],
                    client_type=user["client_type"],
                    client_unique_identifier=user["client_unique_identifier"]
                )

            # wait for events
            while True:
                try:
                    (event_type, event) = self.ts.wait_for_event()
                    print(f"new Event of type {event_type}: {event}")

                    # parse and store in db
                    self.process_event(event_type, event)

                except (TS3ParserError, AttributeError) as e:
                    print(f"Unexpected exception during parsing: {e}")

        except TS3Error as e:
            print(f"Teamspeak error {e}, retry connection")
        except Exception as e:
            print(f"Very unexpected Exception: {e}")
            print(traceback.format_exc())

    # TODO maybe move into client?
    def process_event(self, event_type: str, event: dict):
        # see https://yat.qa/ressourcen/server-query-notify/
        # also https://www.teamspeak-info.de/downloads/ts3_serverquery_manual_stand_19_04_2012.pdf

        match event_type, event['reasonid']:
            case "notifycliententerview", _:
                print(f"client {event['client_nickname']} entered")
                self.client.client_enter(
                    client_id=event["clid"],
                    channel_id=event["ctid"],
                    client_database_id=event["client_database_id"],
                    client_nickname=event["client_nickname"],
                    client_type=event["client_type"],
                    client_unique_identifier=event["client_unique_identifier"]
                )
            case "notifyclientleftview", 0:
                # notifyclientleftview: [{'cfid': '1', 'ctid': '0', 'reasonid': '8', 'reasonmsg': 'Verlassen',
                # 'clid': '3088'}]

                print(f"Client {event['clid']} left", event)
                self.client.client_leave(
                    client_id=event["clid"],
                    channel_id=event["cfid"],
                    reason_id=event["reasonid"]
                )
            case "notifyclientmoved", _:
                # notifyclientmoved: [{'ctid': '37', 'reasonid': '0', 'clid': '2760'}]
                print(f"Client {event['clid']} moved", event)
                self.client.client_move(
                    client_id=event["clid"],
                    channel_to_id=event["ctid"],
                    reason_id=event["reasonid"]
                )
