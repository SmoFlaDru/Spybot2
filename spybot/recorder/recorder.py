import time
import traceback
from threading import Thread
from typing import Dict

import ts3
from django.conf import settings
from ts3 import TS3Error
from ts3.query import TS3ServerConnection, TS3TimeoutError
from ts3.response import TS3ParserError

from spybot.models import TSUserActivity, TSUser, TSID


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

                self.main_loop(ts3conn)

            # prepare for reconnection attempt
            print("Error, restarting connection")

    def main_loop(self, ts3conn: TS3ServerConnection):
        try:
            ts3conn.exec_("clientupdate", client_nickname="spytwo")
            # fetch all connected clients initally
            client_list_response = ts3conn.exec_("clientlist", "uid")
            len(client_list_response.parsed)
            print(f"Client-list inital: {client_list_response.parsed}")

            for client in client_list_response:
                # enter in db
                self.client_enter(
                    client_id=client["clid"],
                    channel_id=client["cid"],
                    client_database_id=client["client_database_id"],
                    client_nickname=client["client_nickname"],
                    client_type=client["client_type"],
                    client_unique_identifier=client["client_unique_identifier"]
                )


            # wait for events
            while True:

                try:
                    event = ts3conn.wait_for_event(30)
                    event._parse_data()
                    print(f"new Event of type {event._event}: {event.parsed}")

                    #parse and store in db
                    self.process_event(event._event, event.parsed[0])

                except TS3TimeoutError:
                    # keep-alive query
                    print("Sending TS keep-alive")
                    ts3conn.query("whoami")
                except (TS3ParserError, AttributeError) as e:
                    print(f"Unexpected exception during parsing: {e}")

        except TS3Error as e:
            print(f"Teamspeak error {e}, retry connection")
        except Exception as e:
            print(f"Very unexpected Exception: {e}")
            print(traceback.format_exc())

    def _remove_fields_except(self, d: dict, list_of_keys_to_keep):
        return {key: value for key, value in d.items() if key in list_of_keys_to_keep}

    def process_event(self, event_type: str, event: dict):
        # see https://yat.qa/ressourcen/server-query-notify/
        keys = ["clid", "cid", "client_database_id", "client_nickname",
                     "client_type", "client_unique_identifier"]
        match event_type, event['reasonid']:
            case "notifycliententerview", _:
                print("Someone entered")
                self.client_enter(
                    client_id=event["clid"],
                    channel_id=event["ctid"],
                    client_database_id=event["client_database_id"],
                    client_nickname=event["client_nickname"],
                    client_type=event["client_type"],
                    client_unique_identifier=event["client_unique_identifier"]
                )
            case "notifyclientleftview", 0:
                # notifyclientleftview: [{'cfid': '1', 'ctid': '0', 'reasonid': '8', 'reasonmsg': 'Verlassen', 'clid': '3088'}]
                reason = event["reason_id"]

                print("Someone left", event)
                self.client_leave(
                    client_id=event["clid"],
                    channel_id=event["cfid"],
                    reason_id=event["reasonid"]
                )
            case "notifyclientmoved", _:
                # notifyclientmoved: [{'ctid': '37', 'reasonid': '0', 'clid': '2760'}]
                print("Someone moved", event)
                self.client_move(
                    client_id=event["clid"],
                    channel_to_id=event["ctid"],
                    reason_id=event["reasonid"]
                )
        pass

    """
    client_id: int
    channel_id: int
    client_nickname: string
    client_type: int
    client_unique_identifier: str
    """
    def client_enter(self, client_id: int, channel_id: int, client_database_id: int, client_nickname: str,
                     client_type: int, client_unique_identifier: str):
        print(f"Hello from client {client_nickname}")
        if client_type == 1:
            # because fuck that tsmonitor
            return

        # check if we have this user yet
        tsid = None
        try:
            tsid = TSID.objects.get(ts_id=client_unique_identifier)
            tsuser = tsid.tsuser
            print(f"found existing TSID for user: {tsid} and tsuser: {tsuser}")
        except TSID.DoesNotExist:
            print("did not find TSID for user")
            # create new TSUser and TSID for this client
            u = TSUser(name=client_nickname)
            u.save()
            tsid = TSID(ts_id=client_unique_identifier, tsuser_id=u.id)
            tsid.save()
            print(f"created tsuser {u.name} and tsid {tsid}for new client")

        #TSUser.objects.filter(tsid__exact=)


        # check if client has an existing client_enter for this channel,
        # and it's the latest entry for this user.
        # In that case don't enter anything in the DB

        #TSUserActivity.objects.get(tsuser)

        # enter new client into DB

    """
    client_id:
    channel_id:
    reason_id: 3 for timeout, 0 for intended disconnect
    """
    def client_leave(self, client_id: int, channel_id: int, reason_id: int):
        pass

    def client_move(self,  client_id: int, channel_to_id: int, reason_id: int):
        pass
