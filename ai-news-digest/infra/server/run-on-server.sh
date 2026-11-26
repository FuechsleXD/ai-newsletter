#!/usr/bin/env bash
# Runs shell commands on the server via SSM Run Command and prints status and output
# (local helper for the runbooks in docs/betrieb/; no SSH, no Session Manager plugin needed).
#   infra/server/run-on-server.sh <instance-id> '<command>' ['<command>' ...]
# Uses the AWS CLI credentials of the caller (e.g. AWS_PROFILE=newsdigest).
set -euo pipefail

instance="${1:?usage: run-on-server.sh <instance-id> <command> [<command> ...]}"
shift
(($# > 0)) || { echo "No command given" >&2; exit 1; }

parameters="$(python -c 'import json, sys; print(json.dumps({"commands": sys.argv[1:], "executionTimeout": ["3600"]}))' "$@")"
command_id="$(aws ssm send-command \
  --instance-ids "$instance" \
  --document-name AWS-RunShellScript \
  --parameters "$parameters" \
  --query Command.CommandId --output text)"

echo "Command $command_id sent, waiting ..."
# The waiter gives up after 100 s or fails on an error; poll until the command has finished
while :; do
  status="$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
    --query Status --output text 2>/dev/null || echo Pending)"
  case "$status" in
    Pending | InProgress | Delayed) sleep 5 ;;
    *) break ;;
  esac
done

aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text
echo "Status: $status"
[[ "$status" == "Success" ]]
