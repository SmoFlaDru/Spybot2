temp_file=$(mktemp)
echo $temp_file
ssh elcheapo -C "docker exec spybot-db-1 pg_dump -U postgres spybot" > $temp_file
docker cp $temp_file spybot2-db-1:/import_dump.sql
docker exec --user postgres spybot2-db-1 dropdb -f --if-exists spybot
docker exec --user postgres spybot2-db-1 createdb spybot
docker exec spybot2-db-1 psql -U postgres spybot -f /import_dump.sql
rm "${temp_file}"