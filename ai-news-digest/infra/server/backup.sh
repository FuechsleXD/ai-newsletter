#!/usr/bin/env bash
# Daily database backup (newsdigest-backup.timer; docs/11-deployment-cicd.md):
# pg_dump of the whole database (schemas app and bpm) in the custom format, encrypted in S3.
# Every success is reported as the metric NewsDigest/BackupSucceeded; the alarm
# newsdigest-prod-backup-missing fires when it is missing for a day.
set -euo pipefail

# shellcheck source=/dev/null
source /etc/newsdigest/deploy.env
export AWS_REGION

container="$(docker ps -q \
  --filter label=com.docker.compose.project=ai-news-digest-prod \
  --filter label=com.docker.compose.service=postgres)"
[[ -n "$container" ]] || { echo "BACKUP FAILED: postgres container is not running" >&2; exit 1; }

key="postgres/newsdigest-$(date -u +%Y%m%dT%H%M%SZ).dump"
# The local socket of the official image allows the superuser without password
docker exec "$container" pg_dump -U postgres -d newsdigest --format=custom --compress=9 \
  | aws s3 cp - "s3://$BACKUP_BUCKET/$key" --sse AES256 --only-show-errors

aws cloudwatch put-metric-data --namespace NewsDigest --metric-name BackupSucceeded --value 1
echo "Backup stored as s3://$BACKUP_BUCKET/$key"
