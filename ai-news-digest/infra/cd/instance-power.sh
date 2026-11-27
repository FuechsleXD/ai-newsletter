#!/usr/bin/env bash
# Starts or stops the server outside the run window (roadmap step 16b, cd.yml; also usable by hand
# from ai-news-digest/ with AWS credentials, docs/betrieb/zeitfenster.md).
#   infra/cd/instance-power.sh start <instance-id>
#       Starts the instance if it is stopped and waits until SSM reaches it. Prints
#       "started=true" if it was started here, "started=false" if it was already running.
#   infra/cd/instance-power.sh stop <instance-id> [<max-wait-minutes>]
#       Waits until no digest run is RUNNING (at most 30 minutes by default, then stops anyway;
#       the maintenance job cancels a cut-off run later) and stops the instance.
set -euo pipefail

usage="usage: infra/cd/instance-power.sh start|stop <instance-id> [<max-wait-minutes>]"
action="${1:?$usage}"
instance="${2:?$usage}"
max_wait_minutes="${3:-30}"
run_on_server="$(dirname "$0")/../server/run-on-server.sh"

state() {
  aws ec2 describe-instances --instance-ids "$instance" \
    --query 'Reservations[0].Instances[0].State.Name' --output text
}

wait_for_ssm() {
  for _ in $(seq 1 60); do
    ping="$(aws ssm describe-instance-information \
      --filters "Key=InstanceIds,Values=$instance" \
      --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)"
    [[ "$ping" == "Online" ]] && return 0
    sleep 5
  done
  echo "The instance $instance is running, but SSM does not reach it after 5 minutes" >&2
  return 1
}

running_runs() {
  # The command runs on the server, so its $(...) must not expand here
  # shellcheck disable=SC2016
  "$run_on_server" "$instance" \
    'c="$(docker ps -q --filter label=com.docker.compose.project=ai-news-digest-prod --filter label=com.docker.compose.service=postgres)"; [ -z "$c" ] && { echo "running-runs=0"; exit 0; }; echo "running-runs=$(docker exec "$c" psql -U postgres -d newsdigest -tAc "SELECT count(*) FROM app.digest_run WHERE status = '"'"'RUNNING'"'"'")"' \
    | sed -n 's/^running-runs=//p' | head -1
}

case "$action" in
  start)
    current="$(state)"
    case "$current" in
      running)
        wait_for_ssm
        echo "started=false"
        ;;
      stopped | stopping)
        if [[ "$current" == "stopping" ]]; then
          echo "Instance $instance is stopping, waiting ..." >&2
          aws ec2 wait instance-stopped --instance-ids "$instance"
        fi
        echo "Starting instance $instance ..." >&2
        aws ec2 start-instances --instance-ids "$instance" >/dev/null
        aws ec2 wait instance-running --instance-ids "$instance"
        wait_for_ssm
        echo "started=true"
        ;;
      pending)
        aws ec2 wait instance-running --instance-ids "$instance"
        wait_for_ssm
        echo "started=false"
        ;;
      *)
        echo "Instance $instance is $current, cannot start it" >&2
        exit 1
        ;;
    esac
    ;;
  stop)
    deadline=$((SECONDS + max_wait_minutes * 60))
    while :; do
      count="$(running_runs || echo unknown)"
      [[ "$count" == "0" ]] && break
      if ((SECONDS >= deadline)); then
        echo "Still $count running run(s) after $max_wait_minutes minutes, stopping anyway" >&2
        break
      fi
      echo "Waiting for $count running run(s) to finish ..." >&2
      sleep 60
    done
    echo "Stopping instance $instance ..." >&2
    aws ec2 stop-instances --instance-ids "$instance" >/dev/null
    aws ec2 wait instance-stopped --instance-ids "$instance"
    echo "Instance $instance stopped" >&2
    ;;
  *)
    echo "$usage" >&2
    exit 1
    ;;
esac
