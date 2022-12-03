echo starting to deploy
echo "$SECRET" > .env
# maybe git pull here required idk?
git pull origin master

# run DB migrations if necessary
python manage.py migrate

# run django app
python manage.py runserver