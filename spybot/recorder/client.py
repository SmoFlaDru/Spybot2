from spybot.models import TSID, TSUser


class Client:

    # client_id: int
    # channel_id: int
    # client_nickname: string
    # client_type: int
    # client_unique_identifier: str
    def client_enter(self, client_id: int, channel_id: int, client_database_id: int, client_nickname: str,
                     client_type: int, client_unique_identifier: str):
        print(f"client {client_nickname} entered")
        if client_type == "1":
            # because fuck that tsmonitor
            return

        # check if we have this user yet
        tsid = None
        try:
            tsid = TSID.objects.get(ts_id=client_unique_identifier)
            tsuser = tsid.tsuser
            print(f"found existing TSID for user: {tsid} and tsuser: {tsuser.name}")
        except TSID.DoesNotExist:
            print("did not find TSID for user")
            # create new TSUser and TSID for this client
            u = TSUser(name=client_nickname)
            u.save()
            tsid = TSID(ts_id=client_unique_identifier, tsuser_id=u.id)
            tsid.save()
            print(f"created tsuser {u.name} and tsid {tsid}for new client")

        # TSUser.objects.filter(tsid__exact=)

        # check if client has an existing client_enter for this channel,
        # and it's the latest entry for this user.
        # In that case don't enter anything in the DB

        # TSUserActivity.objects.get(tsuser)

        # enter new client into DB

    # client_id:
    # channel_id:
    # reason_id: 3 for timeout, 0 for intended disconnect
    def client_leave(self, client_id: int, channel_id: int, reason_id: int):
        pass

    def client_move(self, client_id: int, channel_to_id: int, reason_id: int):
        pass
