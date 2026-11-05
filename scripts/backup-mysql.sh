#!/usr/bin/env bash
set -euo pipefail

output="${1:-backups/it-helpdesk-$(date -u +%Y%m%dT%H%M%SZ).sql}"
output_dir="$(dirname "$output")"
output_name="$(basename "$output")"
mkdir -p "$output_dir"
umask 077
temporary_output="$(mktemp "$output_dir/.${output_name}.tmp.XXXXXX")"
trap 'rm -f "$temporary_output"' EXIT
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > "$temporary_output"
chmod 600 "$temporary_output"
mv -f "$temporary_output" "$output"
trap - EXIT
printf 'Backup written to %s\n' "$output"
