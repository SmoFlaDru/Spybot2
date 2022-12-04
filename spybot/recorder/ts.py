from django.conf import settings
from ts3.query import TS3ServerConnection, TS3TimeoutError


# TODO maybe make this thread safe singleton
class TS:

    def __init__(self):
        # TODO define it better so line 21 doesn't error anymore
        self.ts3conn: TS3ServerConnection

        self.ts_user = settings.TS_USER
        self.ts_password = settings.TS_PASSWORD
        self.ts_ip = settings.TS_IP
        self.ts_port = settings.TS_PORT

        self.EVENT_TIMEOUT = 30

    def make_conn(self):
        conn = f"telnet://{self.ts_user}:{self.ts_password}@{self.ts_ip}:{self.ts_port}"
        self.ts3conn = TS3ServerConnection(conn)
        self.ts3conn.exec_("use", sid=1)

    def register_events(self):
        # Register for events
        self.ts3conn.exec_("servernotifyregister", event="channel", id=0)
        self.ts3conn.exec_("servernotifyregister", event="server")

    def wait_for_event(self):

        # not happy with yet another nested while True loop :/
        while True:
            try:
                res = self.ts3conn.wait_for_event(30)
                res._parse_data()
                return res._event, res.parsed[0]
            except TS3TimeoutError:
                print("Sending TS keep-alive")
                self.keep_alive()

    def get_clients(self):
        return self.ts3conn.exec_("clientlist", "uid").parsed

    def get_channels(self):
        # TODO not really sure about that command
        return self.ts3conn.exec_("channellist").parsed

    def keep_alive(self):
        self.ts3conn.send_keepalive()

    def set_nickname(self, name: str):
        # TODO Teamspeak error error id 513: nickname is already in use, retry connection
        # ts3conn.exec_("clientupdate", client_nickname="spytwo")
        pass
