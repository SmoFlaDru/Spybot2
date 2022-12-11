from django import template

register = template.Library()

@register.filter
def replace_ts_special_chars(value):
    """
    Replacing ts special chars
    Use `{{ "aaa"|replace_ts_special_chars }}`
    """
    ts_special_chars = {
        "\\\\": "\\",
        "\\/": "/",
        "\\s": " ",
        "\\p": "|",
        "\\;": ";",
        "\\a": "\a",
        "\\b": "\b",
        "\\f": "\f",
        "\\n": "\n",
        "\\r": "\r",
        "\\t": "\t",
        "\\v": "\v",
    }

    for search, replace in ts_special_chars.items():
        value = value.replace(search, replace)

    return value
