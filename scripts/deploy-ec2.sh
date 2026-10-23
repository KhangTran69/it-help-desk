#!/usr/bin/env bash
set -euo pipefail

image="${1:?Image URI is required}"
region="${2:?AWS region is required}"
env_parameter="${3:?Secure Systems Manager parameter name is required}"
registry="${image%%/*}"
app_dir="/opt/it-helpdesk"
env_file="$app_dir/app.env"
old_image=""

mkdir -p "$app_dir"
chmod 700 "$app_dir"
aws ssm get-parameter --name "$env_parameter" --with-decryption --query Parameter.Value --output text --region "$region" > "$env_file"
chmod 600 "$env_file"
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"
if docker inspect it-helpdesk >/dev/null 2>&1; then
  old_image="$(docker inspect it-helpdesk --format '{{.Config.Image}}')"
  docker stop it-helpdesk || true
  docker rm it-helpdesk || true
fi

start_container() {
  docker run -d --name it-helpdesk --restart unless-stopped --env-file "$env_file" \
    -p 127.0.0.1:8080:8080 "$1" >/dev/null
}

start_container "$image"
for attempt in $(seq 1 30); do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
    printf 'Staging is healthy on image %s\n' "$image"
    exit 0
  fi
  sleep 5
done

docker logs --tail 100 it-helpdesk >&2 || true
docker stop it-helpdesk || true
docker rm it-helpdesk || true
if [[ -n "$old_image" ]]; then
  printf 'New image failed health check; rolling back to %s\n' "$old_image" >&2
  start_container "$old_image"
  for attempt in $(seq 1 30); do
    if curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
      exit 1
    fi
    sleep 5
  done
  printf 'Previous image did not recover health.\n' >&2
fi
exit 1
