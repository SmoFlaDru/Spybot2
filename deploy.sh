cd "$HOME/Spybot2" || exit || return 1
echo "starting to deploy..."

ls -al
echo "$SECRET" > .env

python -m pip install --upgrade pip
# pyenv smth smth
pip install -r requirements.txt

git pull origin master

# run DB migrations if necessary
python manage.py migrate

# run django app
python manage.py runserver