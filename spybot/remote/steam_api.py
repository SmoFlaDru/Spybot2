import requests

from Spybot2 import settings


def get_steam_user_playing_info(steam_id: str):
    steam_info = get_steam_account_info(steam_id)
    game_id = steam_info.get('gameid', 0)
    game_name = steam_info.get('gameextrainfo', "")
    if game_id != 0:
        return game_id, game_name
    return None, None


def get_steam_account_info(steam_id: str):
    steam_api_key = settings.STEAM_API_KEY
    req = "http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key={key}&steamids={id}" \
        .format(key=steam_api_key, id=steam_id)

    steam_data = requests.get(req)
    steam_info_players = steam_data.json().get('response').get('players')
    if len(steam_info_players) == 0:
        return None
    return steam_info_players[0]