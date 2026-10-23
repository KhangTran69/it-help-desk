#!/usr/bin/env bash
set -euo pipefail

output="${1:-backups/it-helpdesk-$(date -u +%Y%m%dT%H%M%SZ).sql}"
mkdir -p "$(dirname "$output")"
umask 077
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > "$output"
printf 'Backup written to %s\n' "$output"
