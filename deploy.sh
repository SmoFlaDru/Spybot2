echo "starting to deploy..."
ls -al
cd "$HOME/Spybot2" || exit || return 1
python -m pip install --upgrade pip
pip install -r requirements.txt

echo "$SECRET" > .env
git pull origin master

# run DB migrations if necessary
python manage.py migrate

# run django app
python manage.py runserver