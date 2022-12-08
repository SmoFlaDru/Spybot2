from django.urls import path

from . import views

urlpatterns = [
    path('live/', views.live, name='live'),
    path('liver/', views.live, name='live_db'),
    path('helloworld/', views.helloworld, name='helloworld'),
    path('', views.home, name='home'),
]

