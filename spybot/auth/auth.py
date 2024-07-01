from django.contrib.auth import authenticate, login, logout
from django.shortcuts import redirect


def link_login(request):
    user = authenticate(request, username=None, password=None)
    if user is not None:
        login(request, user, 'spybot.auth.backend.link_backend.LinkAuthBackend')
    return redirect("/")


def logout_view(request):
    logout(request)
    return redirect("/")
