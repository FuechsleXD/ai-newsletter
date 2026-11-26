#!/usr/bin/env bash
# Smoke test of production over HTTPS (cd.yml, after every deployment and rollback):
# the public health check reports UP and the start page answers.
#   infra/cd/smoke-test-prod.sh <domain> [<attempts>]
# The internal check against Actuator is part of deploy.sh: "docker compose up --wait" only
# succeeds once the health check of the app container (Actuator) is healthy.
set -euo pipefail

domain="${1:?usage: infra/cd/smoke-test-prod.sh <domain> [<attempts>]}"
attempts="${2:-12}"
base="https://$domain"

for ((attempt = 1; attempt <= attempts; attempt++)); do
  health="$(curl -fsS --max-time 10 "$base/health" 2>/dev/null || true)"
  if [[ "$health" == *'"status":"UP"'* ]] && curl -fsS --max-time 10 "$base/" >/dev/null; then
    echo "Smoke test passed: $base/health is UP, $base/ answers"
    exit 0
  fi
  echo "Attempt $attempt/$attempts: not ready yet (${health:-no answer})"
  if ((attempt < attempts)); then
    sleep 10
  fi
done

echo "Smoke test failed: $base/health did not report UP" >&2
exit 1
