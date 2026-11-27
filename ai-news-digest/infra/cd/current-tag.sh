#!/usr/bin/env bash
# Prints the image tag that runs on the server now (rollback target of cd.yml), or nothing if
# there was no deployment yet. Fails with a clear message if the instance is not reachable via
# SSM; cd.yml starts a stopped instance before (instance-power.sh).
#   infra/cd/current-tag.sh <instance-id>
set -euo pipefail

instance="${1:?usage: infra/cd/current-tag.sh <instance-id>}"

# The command runs on the server, so its $(...) must not expand here
# shellcheck disable=SC2016
if ! output="$("$(dirname "$0")/../server/run-on-server.sh" "$instance" \
  'echo "current-tag=$(cat /etc/newsdigest/current-tag 2>/dev/null)"' 2>&1)"; then
  echo "$output" >&2
  echo "The instance $instance is not reachable via SSM. Is it stopped? Start it first" \
    "with infra/cd/instance-power.sh start (docs/betrieb/zeitfenster.md)." >&2
  exit 1
fi
tag="$(sed -n 's/^current-tag=//p' <<<"$output" | head -1)"
if [[ -n "$tag" && ! "$tag" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Unexpected tag on the server: $tag" >&2
  exit 1
fi
echo "$tag"
