from spybot.models import LoginLink, MergedUser


class LinkAuthBackend:
    """
    Custom authentication backend.

    Allows users to log in using a link code
    """

    def authenticate(self, request, username=None, password=None) -> MergedUser:
        """
        Overrides the authenticate method to allow users to log in using their email address.
        """
        code = request.GET.get("code", None)
        if code is None:
            return None

        try:
            link = LoginLink.objects.get(code=code)
            return link.user

        except LoginLink.DoesNotExist:
            print("Could not authenticate using login link")
            return None

    def get_user(self, user_id) -> MergedUser:
        """
        Overrides the get_user method to allow users to log in using their email address.
        """
        try:
            return MergedUser.objects.get(pk=user_id)
        except MergedUser.DoesNotExist:
            return None
