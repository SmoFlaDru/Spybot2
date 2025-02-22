from django.contrib.auth.decorators import login_required
from django.shortcuts import render

from spybot.models import SteamID


@login_required
def profile_steamids_data(request):
    steamids = SteamID.objects.filter(merged_user=request.user).all()
    return {"profile_steamids": {"steamids": steamids}}


@login_required
def fragment(request):
    return render(request, "spybot/", profile_steamids_data(request))
