#!/usr/bin/env bash
set -euo pipefail

input="${1:?Usage: scripts/restore-mysql.sh path/to/backup.sql}"
if [[ ! -f "$input" ]]; then
  printf 'Backup file not found: %s\n' "$input" >&2
  exit 2
fi
printf 'This imports SQL into the database configured by the current Compose environment. Type RESTORE to continue: '
read -r confirmation
if [[ "$confirmation" != "RESTORE" ]]; then
  printf 'Restore cancelled.\n'
  exit 1
fi
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < "$input"
printf 'SQL import completed. Verify application health and expected records.\n'
