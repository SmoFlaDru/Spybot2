from django.shortcuts import render

from spybot import visualization
from spybot.views.common import get_context, generate_options


def activity_chart_data(request):
    allowed_time_spans = [7, 14, 30, 90]

    options, time_span, time_span_text = generate_options(allowed_time_spans, lambda n: f"Last {n} days", request.GET.get("timespan", None))

    context = get_context(request)
    context['active_option_text'] = time_span_text
    context['options'] = options

    data = visualization.daily_activity(time_span)
    dates, active_values, afk_values = zip(*data) if len(data) > 0 else ((), (), ())
    # convert tuples back to lists when passing to template
    context['daily_dates'] = list(dates)
    context['daily_active_values'] = list(active_values)
    context['daily_afk_values'] = list(afk_values)

    context["options"] = options
    return context


def fragment(request):
    return render(request, 'spybot/home/activity_fragment.html', activity_chart_data(request))
