#!/bin/bash

source venv/bin/activate



# install any missing python deps
pip install -r requirements.txt

# create django superuser if necessary.
# Set the env vars DJANGO_SUPERUSER_{PASSWORD,USERNAME,EMAIL} when running this script
python manage.py createsuperuser --noinput

# run DB migrations if necessary
python manage.py migrate

# start cronjobs with crontab using django-crontab
python manage.py crontab add

# run django app
PYTHONUNBUFFERED=1 python manage.py runserver 0.0.0.0:8000
