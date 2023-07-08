from django.urls import path

from . import views

urlpatterns = [
    path('live/', views.live, name='live'),
    path('spybot/', views.spybot, name='bs'),
    path('helloworld/', views.helloworld, name='helloworld'),
    path('', views.home, name='home'),
    path('widget_legacy', views.widget_legacy, name='widget_legacy'),
    path('timeline', views.timeline, name='timeline'),
    path('halloffame', views.halloffame, name='halloffame'),
    path('u/<int:user_id>', views.user, name='user'),
    path('live_fragment', views.live_fragment, name='live_fragment'),
]

