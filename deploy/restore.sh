#!/usr/bin/env bash
# Restore an ELOKA partner backup made by deploy/backup.sh. DESTRUCTIVE: replaces the current
# database and documents. Needs the same deploy/.env (crypto keys) the backup was made with.
#   deploy/restore.sh <backup-dir> <STAMP>      e.g. deploy/restore.sh backups 20261016T020000Z
set -euo pipefail

DIR="$(cd "${1:?backup dir}" && pwd)" # resolved from where the script was called; absolute for the mount below
cd "$(dirname "$0")"
STAMP="${2:?backup stamp, e.g. 20261016T020000Z}"
DB="$DIR/partner-db-$STAMP.sql.gz"
FILES="$DIR/partner-files-$STAMP.tar.gz"
COMPOSE=(docker compose -f docker-compose.yml --env-file "${ELOKA_ENV_FILE:-.env}")
[[ -n "${ELOKA_COMPOSE_PROJECT:-}" ]] && COMPOSE+=(-p "$ELOKA_COMPOSE_PROJECT")

( cd "$DIR" && shasum -a 256 -c "partner-$STAMP.sha256" )

if [[ "${ELOKA_RESTORE_CONFIRM:-}" != "yes" ]]; then
	read -r -p "This replaces the live database and documents with $STAMP. Type 'restore' to continue: " answer
	[[ "$answer" == "restore" ]] || { echo "aborted"; exit 1; }
fi

echo "[restore] stopping traffic (edge, client, server)"
"${COMPOSE[@]}" stop edge client server

echo "[restore] database (recreated, so tables from newer migrations do not linger)"
"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE IF EXISTS eloka_partner; CREATE DATABASE eloka_partner"'
gunzip -c "$DB" | "${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

echo "[restore] documents"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint sh -v "$DIR:/restore:ro" server -c \
	'rm -rf /app/data/* && tar -xzf "/restore/partner-files-'"$STAMP"'.tar.gz" -C /app'

echo "[restore] migrating (in case the backup is older than the code) and starting"
"${COMPOSE[@]}" up -d --wait
echo "[restore] done"
