from typing import List
import requests

from Spybot2 import settings

def get_steam_users_playing_info(steam_ids: List[str]):
    assert 0 < len(steam_ids) <= 100

    return [
        (
            steam_info.get("steamid", ""),
            steam_info.get("gameid", 0),
            steam_info.get("gameextrainfo", "")
        )
        for steam_info in _get_steam_accounts_info(steam_ids)
    ]


def _get_steam_accounts_info(steam_ids: List[str]):
    steam_api_key = settings.STEAM_API_KEY
    req = "http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key={key}&steamids={id}".format(
        key=steam_api_key, id=",".join(steam_ids)
    )

    steam_data = requests.get(req)
    steam_info_players = steam_data.json().get("response").get("players")
    if len(steam_info_players) == 0:
        return []
    return steam_info_players
