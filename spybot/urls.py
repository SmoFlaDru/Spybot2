from django.urls import path

from .auth import auth
from . import api
from .auth import passkeys
from .views import views
from .views.fragments import activity_chart

urlpatterns = [
    path('live/', views.live, name='live'),
    path('', views.home, name='home'),
    path('timeline', views.timeline, name='timeline'),
    path('halloffame', views.halloffame, name='halloffame'),
    path('u/<int:user_id>', views.user, name='user'),
    path('live_fragment', views.live_fragment, name='live_fragment'),
    path('activity_fragment', activity_chart.fragment, name='activity_fragment'),
    path('recent_events_fragment', views.recent_events_fragment, name='recent_events_fragment'),
    path('profile', views.profile, name='profile'),
    path('profile/passkey/<str:id>', views.profile_passkey, name='profile_passkey'),
    path('profile/steamid', views.profile_add_steamid, name='profile_add_steamid'),
    path('profile/steamid/all', views.profile_steamids_fragment, name='profile_steamids_fragment'),
    path('profile/steamid/<str:id>', views.profile_delete_steamid, name='profile_delete_steamid'),
    path('login', views.login, name='login'),
    path('login_teamspeak', views.login_teamspeak, name='login_teamspeak'),
    path('link_auth', auth.link_login, name='link_login'),
    path('logout', auth.logout_view, name='logout'),
    path('passkeys/generate-authentication-options', passkeys.generate_authentication_options, name='passkeys_generate-authentication-options'),
    path('passkeys/generate-registration-options', passkeys.generate_registration_options, name='passkeys_generate-registration-options'),
    path('passkeys/verify-registration', passkeys.verify_registration, name='passkeys_verify-registration'),
    path('passkeys/verify-authentication', passkeys.verify_authentication, name='passkeys_verify-authentication'),

    # API
    path('api/v1/live', api.live_api, name='live_api'),
    path('api/v1/widget', api.widget_legacy, name='widget_api'),
    path('widget_legacy', api.widget_legacy, name='widget_legacy'),
]

