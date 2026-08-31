#!/bin/bash
set -e

CELERY_WORKER_JOB=""
CELERY_BEAT_JOB=""
RECORDER_JOB=""
GUNICORN_JOB=""
TERMINATING=0

terminate() {
    if [ "$TERMINATING" -eq 1 ]; then
        return
    fi

    TERMINATING=1
    trap - EXIT HUP TERM INT

    for job in "$GUNICORN_JOB" "$RECORDER_JOB" "$CELERY_BEAT_JOB" "$CELERY_WORKER_JOB"; do
        if [ -n "$job" ]; then
            kill -TERM "$job" 2>/dev/null || true
        fi
    done

    wait 2>/dev/null || true
}

trap terminate EXIT HUP TERM INT

# copy static files to directory for http server
.venv/bin/python manage.py collectstatic --noinput

# run DB migrations if necessary
.venv/bin/python manage.py migrate

# run celery worker process
.venv/bin/python -m celery -A Spybot2 worker -l info &
CELERY_WORKER_JOB=$!

# run celery beat process
.venv/bin/python -m celery -A Spybot2 beat -l info &
CELERY_BEAT_JOB=$!

# start recorder in background and terminate on script exit
.venv/bin/python manage.py recorder &
RECORDER_JOB=$!

# run django app
.venv/bin/gunicorn -w 2 --bind 0.0.0.0:8000 Spybot2.wsgi &
GUNICORN_JOB=$!
wait "$GUNICORN_JOB"
