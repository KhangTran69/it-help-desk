#!/usr/bin/env bash
set -euo pipefail

port="${BENCH_PORT:-18090}"
url="http://127.0.0.1:$port"
samples="$(mktemp)"
log="$(mktemp)"
cleanup() {
  if [[ -n "${app_pid:-}" ]]; then kill "$app_pid" 2>/dev/null || true; wait "$app_pid" 2>/dev/null || true; fi
  rm -f "$samples" "$log"
}
trap cleanup EXIT

mvn -q -DskipTests package
java -jar target/it-helpdesk-0.0.1-SNAPSHOT.jar --spring.profiles.active=benchmark --server.port="$port" >"$log" 2>&1 &
app_pid=$!
for attempt in $(seq 1 60); do
  if curl --fail --silent "$url/actuator/health" >/dev/null; then break; fi
  if ! kill -0 "$app_pid" 2>/dev/null; then cat "$log" >&2; exit 1; fi
  sleep 2
done
curl --fail --silent "$url/actuator/health" >/dev/null || { cat "$log" >&2; exit 1; }
token="$(curl --fail --silent -H 'Content-Type: application/json' -d '{"username":"bench-technician","password":"benchmark-tech-123"}' "$url/api/auth/login" | jq -r .token)"
if [[ -z "$token" || "$token" == null ]]; then printf 'Could not obtain benchmark token.\n' >&2; exit 1; fi

for request in $(seq 1 100); do
  curl --fail --silent -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer $token" \
    "$url/api/tickets?page=0&size=20&sortBy=updatedAt&sortDir=desc" >> "$samples"
done
printf 'Dataset: 1,000 synthetic tickets and 250 synthetic assets; H2 in-memory, Redis cache disabled; 100 serial GET /api/tickets requests, page size 20.\n'
sort -n "$samples" | awk '{ values[NR]=$1; total+=$1 } END { p95=int(NR*0.95+0.999); printf "Samples: %d\nMean latency: %.4f s\nP95 latency: %.4f s\n", NR, total/NR, values[p95] }'
