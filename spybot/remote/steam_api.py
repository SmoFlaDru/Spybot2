from dataclasses import dataclass
from enum import Enum
from typing import List
import requests
from requests.exceptions import RequestException

from Spybot2 import settings


class OnlineStatus(Enum):
    OFFLINE = 0
    ONLINE = 1
    BUSY = 2
    AWAY = 3
    SNOOZE = 4


@dataclass
class SteamAccountInfo:
    steam_id: str
    game_id: int
    game_name: str
    avatar_url: str
    online_status: OnlineStatus


def get_steam_users_playing_info(steam_ids: List[str]):
    assert 0 < len(steam_ids) <= 100

    return [
        SteamAccountInfo(
            steam_info.get("steamid", ""),
            steam_info.get("gameid", 0),
            steam_info.get("gameextrainfo", ""),
            steam_info.get("avatar", ""),
            OnlineStatus(steam_info.get("personastate", 0)),
        )
        for steam_info in _get_steam_accounts_info(steam_ids)
    ]


def _get_steam_accounts_info(steam_ids: List[str]):
    try:
        steam_api_key = settings.STEAM_API_KEY
        req = "http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key={key}&steamids={id}".format(
            key=steam_api_key, id=",".join(steam_ids)
        )

        response = requests.get(req)
        response.raise_for_status()
        steam_info_players = response.json().get("response").get("players")
        if len(steam_info_players) == 0:
            return []
        return steam_info_players
    except RequestException as e:
        print("Error getting data from steam API: ", e)
        return []
