#!/usr/bin/env bash
set -euo pipefail

compose_project="it-helpdesk-smoke-${GITHUB_RUN_ID:-local}"
app_port="${APP_PORT:-18080}"
export APP_PORT="$app_port"
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_DATABASE=it_helpdesk
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-smoke-root-password}"
export DB_USERNAME=helpdesk
export DB_PASSWORD="${DB_PASSWORD:-smoke-app-password}"
export DB_URL='jdbc:mysql://mysql:3306/it_helpdesk?serverTimezone=UTC'
export REDIS_HOST=redis
export REDIS_PORT=6379
export JWT_SECRET="${JWT_SECRET:-compose-smoke-jwt-secret-at-least-32-bytes}"

compose=(docker compose -p "$compose_project" -f docker-compose.yml)
cleanup() {
  "${compose[@]}" down --volumes --remove-orphans
}
trap cleanup EXIT

"${compose[@]}" up --build --detach app mysql redis

base_url="http://127.0.0.1:${APP_PORT}"
for attempt in $(seq 1 90); do
  if curl --fail --silent "$base_url/actuator/health" >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 90 ]]; then
    "${compose[@]}" logs app mysql redis
    echo "Application did not become healthy within 90 seconds." >&2
    exit 1
  fi
  sleep 2
done

curl --fail --silent "$base_url/login" | grep -q 'Sign in to manage support requests.'
curl --fail --silent "$base_url/register" | grep -q 'Create employee account'
echo "Compose smoke check passed: health, login, and employee registration pages responded."
