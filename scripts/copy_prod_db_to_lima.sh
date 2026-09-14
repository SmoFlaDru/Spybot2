#!/usr/bin/env bash
#
# Copies the production database into the Postgres container of the local Docker stack, which
# runs inside the "spybot-dev" Lima VM. Run this on the Mac, not inside the VM:
#
#   scripts/copy_prod_db_to_lima.sh              # dump production over ssh, then import
#   scripts/copy_prod_db_to_lima.sh dump.sql     # import an existing dump instead
#
# The dump is kept in a temp file (its path is printed) so a failed import can be retried with
# the second form without hitting production again. The app containers are stopped while the
# database is replaced and started again afterwards. Override any of the defaults below through
# the environment, e.g. LIMA_VM=other-vm.
set -euo pipefail

PROD_HOST=${PROD_HOST:-elcheapo}
PROD_DB_CONTAINER=${PROD_DB_CONTAINER:-spybot-db-1}
LIMA_VM=${LIMA_VM:-spybot-dev}
LOCAL_DB_CONTAINER=${LOCAL_DB_CONTAINER:-spybot2-db-1}
DB_NAME=${DB_NAME:-spybot}
DB_USER=${DB_USER:-postgres}

# Runs a command inside the Lima VM. When the script itself already runs inside the VM (no
# limactl around), the command runs directly - handy for testing the import half.
in_vm() {
    if command -v limactl >/dev/null 2>&1; then
        limactl shell "$LIMA_VM" -- "$@"
    else
        "$@"
    fi
}

if command -v limactl >/dev/null 2>&1; then
    status=$(limactl list --format '{{.Status}}' "$LIMA_VM" 2>/dev/null || true)
    if [ "$status" != "Running" ]; then
        echo "Lima VM '$LIMA_VM' is not running (status: ${status:-not found}); start it with: limactl start $LIMA_VM" >&2
        exit 1
    fi
fi

if [ $# -ge 1 ]; then
    dump=$1
    [ -s "$dump" ] || { echo "Dump file '$dump' is missing or empty" >&2; exit 1; }
    echo "Importing existing dump $dump"
else
    dump=$(mktemp -t spybot-prod-dump.XXXXXX)
    echo "Dumping production database from $PROD_HOST to $dump ..."
    ssh -C "$PROD_HOST" "docker exec $PROD_DB_CONTAINER pg_dump -U $DB_USER $DB_NAME" > "$dump"
    [ -s "$dump" ] || { echo "The dump came back empty" >&2; exit 1; }
    echo "Dumped $(du -h "$dump" | cut -f1)"
fi

# Only the DB container may hold connections while the database is dropped; stop the app
# containers of the stack for the duration and remember which ones to bring back.
app_containers=$(in_vm docker ps -q --filter "name=spybot2-spybot-" | tr '\n' ' ')
if [ -n "$app_containers" ]; then
    echo "Stopping app containers while the database is replaced ..."
    # shellcheck disable=SC2086
    in_vm docker stop $app_containers >/dev/null
fi

echo "Replacing database '$DB_NAME' in container $LOCAL_DB_CONTAINER ..."
in_vm docker exec --user "$DB_USER" "$LOCAL_DB_CONTAINER" dropdb --force --if-exists "$DB_NAME"
in_vm docker exec --user "$DB_USER" "$LOCAL_DB_CONTAINER" createdb "$DB_NAME"
in_vm docker exec -i "$LOCAL_DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -v ON_ERROR_STOP=1 < "$dump" > /dev/null

users=$(in_vm docker exec "$LOCAL_DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc "select count(*) from spybot_mergeduser")
echo "Imported: $users merged users"

if [ -n "$app_containers" ]; then
    echo "Starting app containers again ..."
    # shellcheck disable=SC2086
    in_vm docker start $app_containers >/dev/null
fi
echo "Done. The dump is still at $dump - delete it when you no longer need it."
