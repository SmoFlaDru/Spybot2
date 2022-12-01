# install any missing python deps
pip install -r requirements.txt

# create django superuser if necessary.
# Set the env vars DJANGO_SUPERUSER_{PASSWORD,USERNAME,EMAIL} when running this script
python manage.py createsuperuser --noinput

# run DB migrations if necessary
python manage.py migrate

# run django app
python manage.py runserver
