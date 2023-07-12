from django.urls import path

from . import views

urlpatterns = [
    path('live/', views.live, name='live'),
    path('', views.home, name='home'),
    path('widget_legacy', views.widget_legacy, name='widget_legacy'),
    path('timeline', views.timeline, name='timeline'),
    path('halloffame', views.halloffame, name='halloffame'),
    path('u/<int:user_id>', views.user, name='user'),
    path('live_fragment', views.live_fragment, name='live_fragment'),
    path('recent_events_fragment', views.recent_events_fragment, name='recent_events_fragment'),
]

