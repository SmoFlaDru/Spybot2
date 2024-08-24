#!/bin/bash

# install frontend dependencies
cd frontend
npm install
npm run package
cd ..

# activate venv
source venv/bin/activate

# install project python deps
pip3.11 install uv
uv sync

# copy static files to directory for http server
uv run manage.py collectstatic --noinput

# create django superuser if necessary.
# Set the env vars DJANGO_SUPERUSER_{PASSWORD,USERNAME,EMAIL} when running this script
# shellcheck disable=SC2046
env $(grep -E '^DJANGO_SUPERUSER_(PASSWORD|USERNAME|EMAIL)' .env | xargs) python manage.py createsuperuser --noinput

# run DB migrations if necessary
uv run manage.py migrate

# start cronjobs with crontab using django-crontab
uv run manage.py crontab add
# run twice in case jobs have been removed. See: https://github.com/kraiz/django-crontab/blob/master/django_crontab/crontab.py#L209
uv run manage.py crontab add

# run django app
uv run manage.py runserver 127.0.0.1:8000
