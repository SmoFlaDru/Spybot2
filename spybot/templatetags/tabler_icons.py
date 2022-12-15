import textwrap

from django import template
from django.conf import settings
from django.template.defaultfilters import stringfilter
from django.utils.html import format_html

register = template.Library()


@register.simple_tag(takes_context=False)
@stringfilter
def tabler_icon(name, **kwargs):
    """
    Output markup for this tabler icon
    :param **kwargs:
    """
    url = settings.STATIC_URL + "tabler-sprite.svg"
    return format_html(textwrap.dedent("""\
        <svg width="24" height="24" class="{}">
            <use xlink:href="{}#tabler-{}"/>
        </svg>"""),
       kwargs.get("class", ""),
        url,
        name
    )
