from django.utils import timezone

from spybot.models import TSID, TSUser, TSUserActivity, TSChannel


class Client:

    # client_id: int
    # channel_id: int
    # client_nickname: string
    # client_type: int
    # client_unique_identifier: str
    def client_enter(self, client_id: int, channel_id: int, client_database_id: int, client_nickname: str,
                     client_type: int, client_unique_identifier: str):
        if client_type == "1":
            # because fuck that tsmonitor
            return

        # check if we have this user yet
        tsid = None
        try:
            tsid = TSID.objects.get(ts_id=client_unique_identifier)
            tsuser = tsid.tsuser
            tsuser.client_id = client_id
            tsuser.save()
            print(f"found existing TSID for user: {tsuser.name} with id={tsid}")
            self.__client_start_session(tsuser, channel_id)
        except TSID.DoesNotExist as e:
            print(f"did not find TSID for user {e}")
            # create new TSUser and TSID for this client
            u = TSUser(name=client_nickname, client_id=client_id)
            u.save()
            tsid = TSID(tsuser_id=u.id, ts_id=client_unique_identifier)
            tsid.save()
            self.__client_start_session(u, channel_id, True)
            print(f"created tsuser {u.name} and tsid {u.id}for new client")

        # enter new client into DB

    # called on user disconnect
    # client_id: userID
    # channel_id: channel that was left
    # reason_id: 3 for timeout, 0 for intended disconnect, 8 unknown
    def client_leave(self, client_id: int, channel_id: int, reason_id: int):
        user = TSUser.objects.get(client_id__exact=client_id)
        user.client_id = None
        user.save()

        self.__client_end_session(user, reason_id, channel_id)

    def client_move(self, client_id: int, channel_to_id: int, reason_id: int):
        print("Client moved!")
        user = self.__get_user_from_client_id(client_id)

        self.__client_end_session(user, reason_id)
        self.__client_start_session(user, channel_to_id)

    def __client_start_session(self, ts_user: TSUser, channel_id: int, is_new_user: bool = True):
        # TODO maybe joined?, check if already has open session?
        print(f"starting session for {ts_user.id}")

        if not is_new_user:
            # TODO
            # client was already on server when spybot started
            # check if he has an open-ended session and close/remove it
            # maybe sth like this
            # if TSUserActivity.objects.order_by('-startTime').filter(tsuser_id=ts_user.id).exists():
            #   assert (TSUserActivity.objects.order_by('-startTime').get(tsuser_id=ts_user.id).end_time is None)
            pass

        try:
            channel = TSChannel.objects.get(id__exact=channel_id)
            new_activity = TSUserActivity(tsuser_id=ts_user, start_time=timezone.now(), channel_id=channel)
            new_activity.save()
        except TSChannel.DoesNotExist as e:
            print(f"channel ID wrong: {channel_id}")


    def __client_end_session(self, ts_user: TSUser, reason_id: int, channel_id: int = -1):
        print("Ending Session!")
        # old_activity = newest TSUserActivity for client_id (in channel_id)
        # insert endTime, reason_id into old_activity
        try:
            old_activity = TSUserActivity.objects.order_by('-start_time').filter(tsuser_id=ts_user)[0]

            if channel_id != -1:
                assert channel_id == old_activity.channel_id

            if old_activity.end_time is not None:
                raise ValueError('end_time should be empty here')

            old_activity.end_time = timezone.now()
            old_activity.disconnect_id = reason_id
            old_activity.save()
        except Exception as e:  # TODO specify Exception
            print(f"User does not exist or smth: {e}")

    def __get_user_from_client_id(self, client_id: int):
        try:
            ts_user = TSUser.objects.get(client_id__exact=client_id)
            return ts_user
        except TSUser.DoesNotExist as e:
            print(f"Error client ID is wrong: {e}")
