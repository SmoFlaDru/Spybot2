cd "$HOME/Spybot2" || exit || return 1
echo "starting to deploy..."

ls -al

echo "test" > .env

git pull origin master

# restart spybot service here
sudo systemctl restart spybot