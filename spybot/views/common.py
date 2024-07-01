from typing import Callable

from spybot.models import MergedUser


def get_user(request):
    return request.user if isinstance(request.user, MergedUser) else None


def get_context(request):
    return {"logged_in_user": get_user(request)}


def safe_to_int(val: str):
    if val is None:
        return None
    try:
        return int(val)
    except ValueError:
        return None


def generate_options(values: list[int], text_lambda: Callable[[int], str], parameter):
    parameter = safe_to_int(parameter)
    if parameter not in values:
        parameter = values[0]

    options = list(
        {"text": text_lambda(n), "value": n, "active": n == parameter} for n in values)
    parameter_text = next(option["text"] for option in options if option["value"] == parameter)
    return options, parameter, parameter_text
