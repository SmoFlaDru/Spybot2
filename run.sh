#!/bin/bash

# copy static files to directory for http server
.venv/bin/python manage.py collectstatic --noinput

# run DB migrations if necessary
.venv/bin/python manage.py migrate

# run celery worker process
.venv/bin/python -m celery -A Spybot2 worker -l info &
CELERY_WORKER_JOB=$!
trap 'kill CELERY_WORKER_JOB' EXIT HUP TERM INT

# run celery beat process
.venv/bin/python -m celery -A Spybot2 beat -l info &
CELERY_BEAT_JOB=$!
trap 'kill CELERY_BEAT_JOB' EXIT HUP TERM INT

# start recorder in background and terminate on script exit
.venv/bin/python manage.py recorder &
RECORDER_JOB=$!
trap 'kill $RECORDER_JOB' EXIT HUP TERM INT

# run django app
.venv/bin/gunicorn -w 2 --bind 0.0.0.0:8000 Spybot2.wsgi
