cd "$HOME/Spybot2" || exit || return 1
echo "starting to deploy..."

ls -al

git reset --hard HEAD
git pull origin master

# restart spybot service here
sudo systemctl restart spybot