#!/usr/bin/env bash
# Smoke test of docker-compose.prod.yml (CI job "image" and docs/betrieb/prod-compose-lokal.md).
# Starts the production services with throw-away values and DOMAIN=localhost (Caddy's internal CA,
# no Let's Encrypt, no AI or mail calls) and checks what is reachable from the outside.
# Usage: docker/smoke-test.sh <app-image>   (run from ai-news-digest/)
set -euo pipefail

image="${1:?usage: docker/smoke-test.sh <app-image>}"
project="newsdigest-smoke"
env_file="$(mktemp)"
trap 'docker compose -p "$project" -f docker-compose.prod.yml --env-file "$env_file" down -v >/dev/null 2>&1 || true; rm -f "$env_file"' EXIT

cat >"$env_file" <<EOF
DOMAIN=localhost
APP_IMAGE=$image
POSTGRES_PASSWORD=smoke-only
DB_MIGRATION_PASSWORD=smoke-only
DB_APP_PASSWORD=smoke-only
SPRING_MAIL_HOST=mail.invalid
SPRING_MAIL_PORT=25
NEWSDIGEST_MAIL_FROM=digest@example.test
NEWSDIGEST_MAIL_OPERATOR=operator@example.test
EOF

compose() { docker compose -p "$project" -f docker-compose.prod.yml --env-file "$env_file" "$@"; }

compose config --quiet
if ! compose up -d --wait --wait-timeout 240; then
  compose ps
  compose logs --tail 80 app
  exit 1
fi

failures=0
# expect <description> <expected status> <path> [body must contain]
expect() {
  local description="$1" expected="$2" path="$3" needle="${4:-}" body status
  body="$(mktemp)"
  status="$(curl -sk -o "$body" -w '%{http_code}' "https://localhost$path")"
  if [[ "$status" != "$expected" ]] || { [[ -n "$needle" ]] && ! grep -q "$needle" "$body"; }; then
    echo "FAIL  $description: $path -> $status (expected $expected${needle:+, body with \"$needle\"})"
    failures=$((failures + 1))
  else
    echo "OK    $description: $path -> $status"
  fi
  rm -f "$body"
}

expect "Health check" 200 /health '"status":"UP"'
expect "Placeholder page" 200 / "AI News Digest"
# Not published: unknown paths get the placeholder page, never the app
expect "Actuator not public" 200 /actuator/env "Diese Seite befindet sich im Aufbau"
expect "Dev endpoint not public" 200 /dev/topics/1/runs "Diese Seite befindet sich im Aufbau"
expect "API not public" 200 /api/topics "Diese Seite befindet sich im Aufbau"

http_status="$(curl -s -o /dev/null -w '%{http_code}' http://localhost/)"
if [[ "$http_status" == "308" || "$http_status" == "301" ]]; then
  echo "OK    HTTP redirects to HTTPS: / -> $http_status"
else
  echo "FAIL  HTTP redirects to HTTPS: / -> $http_status"
  failures=$((failures + 1))
fi

if ((failures > 0)); then
  compose logs --tail 80 app caddy
  exit 1
fi
echo "Smoke test passed"
