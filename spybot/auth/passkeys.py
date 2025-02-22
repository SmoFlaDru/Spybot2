import json
import logging
import traceback
from base64 import urlsafe_b64encode

import fido2
from django.contrib.auth import get_user_model, login
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt
from fido2.server import Fido2Server
from fido2.utils import websafe_decode, websafe_encode
from fido2.webauthn import (
    PublicKeyCredentialRpEntity,
    AttestedCredentialData,
    PublicKeyCredentialUserEntity,
)
from user_agents.parsers import parse as user_agent_parse

from Spybot2 import settings
from spybot.models import UserPasskey, MergedUser

log = logging.getLogger(__name__)

fido2.features.webauthn_json_mapping.enabled = True


def get_server(request=None):
    """Get Server Info from settings and returns a Fido2Server"""
    fido_server_id = settings.SERVER_IP.split(":")[0]
    fido_server_name = settings.FIDO_SERVER_NAME

    rp = PublicKeyCredentialRpEntity(id=fido_server_id, name=fido_server_name)
    return Fido2Server(rp)


def get_user_credentials(user: MergedUser):
    User = get_user_model()
    username_field = User.USERNAME_FIELD
    filter_args = {"user__" + username_field: user.id}
    return [
        AttestedCredentialData(websafe_decode(uk.token))
        for uk in UserPasskey.objects.filter(**filter_args)
    ]


def user_agent_info(request):
    ua = user_agent_parse(request.META["HTTP_USER_AGENT"])
    return ua.get_device(), ua.get_os(), ua.get_browser()


def generate_authentication_options(request):
    server = get_server(request)
    credentials = []
    _username = None
    if "base_username" in request.session:
        _username = request.session["base_username"]
    if request.user.is_authenticated:
        _username = request.user.username
    # if username:
    #     credentials = getUserCredentials(username)
    auth_data, state = server.authenticate_begin(credentials)
    request.session["fido2_state"] = state
    return JsonResponse(dict(auth_data))


def generate_registration_options(request):
    """Starts registering a new FIDO Device, called from API"""
    server = get_server(request)
    auth_attachment = getattr(settings, "KEY_ATTACHMENT", None)
    registration_data, state = server.register_begin(
        PublicKeyCredentialUserEntity(
            name=request.user.get_full_name(),
            id=urlsafe_b64encode(request.user.username.encode("utf8")),
            display_name=request.user.get_full_name(),
        ),
        get_user_credentials(request.user),
        authenticator_attachment=auth_attachment,
        resident_key_requirement=fido2.webauthn.ResidentKeyRequirement.PREFERRED,
    )
    request.session["fido2_state"] = state
    return JsonResponse(dict(registration_data))


@csrf_exempt
def verify_registration(request):
    """Completes the registeration, called by API"""
    try:
        if "fido2_state" not in request.session:
            return JsonResponse(
                {
                    "status": "ERR",
                    "message": "FIDO Status can't be found, please try again",
                }
            )
        data = json.loads(request.body)
        user_agent_fields = user_agent_info(request)
        name = user_agent_fields[0]
        server = get_server(request)
        auth_data = server.register_complete(
            request.session.pop("fido2_state"), response=data
        )
        encoded = websafe_encode(auth_data.credential_data)
        platform = f"{user_agent_fields[2]} on {user_agent_fields[1]}"
        uk = UserPasskey(user=request.user, token=encoded, name=name, platform=platform)
        if data.get("id"):
            uk.credential_id = data.get("id")

        uk.save()
        return JsonResponse({"status": "OK", "verified": True})
    except Exception:
        print(traceback.format_exc())
        return JsonResponse(
            {"status": "ERR", "message": "Error on server, please try again later"}
        )


@csrf_exempt
def verify_authentication(request):
    credentials = []
    server = get_server(request)
    data = json.loads(request.body)
    key = None
    # userHandle = data.get("response",{}).get('userHandle')
    credential_id = data["id"]
    #
    # if userHandle:
    #     if User_Passkey.objects.filter(=userHandle).exists():
    #         credentials = getUserCredentials(userHandle)
    #         username=userHandle
    #     else:
    #         keys = User_Keys.objects.filter(user_handle = userHandle)
    #         if keys.exists():
    #             credentials = [AttestedCredentialData(websafe_decode(keys[0].properties["device"]))]

    keys = UserPasskey.objects.filter(credential_id=credential_id, enabled=1)
    if keys.exists():
        credentials = [AttestedCredentialData(websafe_decode(keys[0].token))]
        key = keys[0]

        try:
            _cred = server.authenticate_complete(
                request.session.pop("fido2_state"),
                credentials=credentials,
                response=data,
            )
        except ValueError as exception:  # pragma: no cover
            log.error("valueerror in verify_authentication: %s", exception)
            return None  # pragma: no cover
        except Exception as exception:  # pragma: no cover
            log.error("exception in verify_authentication: %s", exception)
            raise Exception(exception)  # pragma: no cover
        if key:
            key.last_used = timezone.now()
            request.session["passkey"] = {
                "passkey": True,
                "name": key.name,
                "id": key.id,
                "platform": key.platform,
                "cross_platform": user_agent_info(request)[1] != key.platform,
            }
            key.save()

            login(request, key.user, "django.contrib.auth.backends.ModelBackend")

            return JsonResponse({"verified": True, "user": key.user.id})
    log.error("no credentials found")
    return None  # pragma: no cover
