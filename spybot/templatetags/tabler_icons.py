from django import template
from django.conf import settings
from django.template.defaultfilters import stringfilter
from django.utils.html import format_html

register = template.Library()


@register.simple_tag(takes_context=False)
@stringfilter
def tabler_icon(name):
    """
    Output markup for this tabler icon
    """
    url = settings.STATIC_URL + "tabler-sprite.svg"
    return format_html("<svg width=\"24\" height=\"24\">\
        <use xlink:href=\"{}#tabler-{}\"/>",
                       url,
                       name
                       )
