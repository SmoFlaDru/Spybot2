import traceback

from ts3 import TS3Error
from ts3.response import TS3ParserError

from django import db

from spybot.recorder.client import Client
from spybot.recorder.ts import TS


class Recorder:
    def __init__(self):
        self.ts = TS()
        self.client = Client(self.ts)

    def run(self):
        while True:
            self.ts.make_conn()
            self.ts.register_events()
            self.ts.set_nickname("Spybot_2")

            self.main_loop()

            # prepare for reconnection attempt
            print("Ultimate Error, restarting connection")

    def main_loop(self):
        try:
            # TODO fetch all channels and update in DB if necessary

            # fetch all connected clients initally
            client_list = self.ts.get_clients()

            print(f"Client-list inital: {client_list}")
            # close old open sessions
            client_list = self.client.handle_old_sessions(client_list)

            for user in client_list:
                # enter in db
                self.client.client_enter(
                    client_id=user["clid"],
                    channel_id=user["cid"],
                    client_database_id=user["client_database_id"],
                    client_nickname=user["client_nickname"],
                    client_type=user["client_type"],
                    client_unique_identifier=user["client_unique_identifier"],
                )

            # wait for events
            while True:
                try:
                    (event_type, event) = self.ts.wait_for_event()
                    print(f"new Event of type {event_type}: {event}")

                    # fix for closed database connection
                    # https://stackoverflow.com/a/78573290
                    db.close_old_connections()

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

        match event_type:
            case "notifycliententerview":
                print(f"event: {event['client_nickname']} entered")
                self.client.client_enter(
                    client_id=int(event["clid"]),
                    channel_id=int(event["ctid"]),
                    client_database_id=int(event["client_database_id"]),
                    client_nickname=event["client_nickname"],
                    client_type=event["client_type"],
                    client_unique_identifier=event["client_unique_identifier"],
                )
            case "notifyclientleftview":
                # TODO wrong there is no reasonid because of ts3 vs ts5
                # clid = client_id, cfid = channel_from_id
                # notifyclientleftview: [{'cfid': '1', 'ctid': '0', 'reasonid': '8', 'reasonmsg': 'Verlassen',
                # 'clid': '3088'}]

                print(f"Client {event['clid']} left", event)
                self.client.client_leave(
                    client_id=event["clid"],
                    channel_id=event["cfid"],
                    reason_id=event.get("reasonid", -1),
                )
            case "notifyclientmoved":
                # notifyclientmoved: [{'ctid': '37', 'reasonid': '0', 'clid': '2760'}]
                print(f"Client {event['clid']} moved", event)
                self.client.client_move(
                    client_id=event["clid"],
                    channel_to_id=event["ctid"],
                    reason_id=event["reasonid"],
                )
