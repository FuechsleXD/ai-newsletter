#!/usr/bin/env bash
# Deploys a release on the server (run as root, via SSM; docs/betrieb/aws-bootstrap.md).
#   deploy.sh <image-tag>     e.g. deploy.sh 4a7a43e
# 1. Fetches the deploy files of the release from S3 (upload-deploy-files.sh)
# 2. Reads the start secrets from AWS Secrets Manager into an env file in /run (memory only,
#    never on disk) and starts docker-compose.prod.yml with the AWS override
# 3. Installs the backup timer
# Non-secret settings come from /etc/newsdigest/deploy.env (written by the Terraform user data).
set -euo pipefail

tag="${1:?usage: deploy.sh <image-tag>}"
[[ "$tag" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Invalid image tag: $tag" >&2; exit 1; }

# shellcheck source=/dev/null
source /etc/newsdigest/deploy.env
app_dir=/opt/newsdigest
env_dir=/run/newsdigest
env_file="$env_dir/prod.env"
export AWS_REGION

secret() {
  aws secretsmanager get-secret-value --secret-id "newsdigest/prod/$1" \
    --query SecretString --output text
}

echo "Fetching deploy files from s3://$DEPLOY_BUCKET/current/"
mkdir -p "$app_dir"
aws s3 sync "s3://$DEPLOY_BUCKET/current/" "$app_dir/" --delete --only-show-errors
chmod 0755 "$app_dir"/infra/server/*.sh

echo "Reading start secrets"
# Separate assignments: a failing read stops the script (set -e) instead of writing an empty value
postgres_password="$(secret db/postgres)"
migration_password="$(secret db/migration)"
app_password="$(secret db/app)"
operator="$(secret mail/operator)"
smtp="$(secret mail/smtp)"
smtp_username="$(jq -er '.username' <<<"$smtp")"
smtp_password="$(jq -er '.password' <<<"$smtp")"
install -d -m 0700 "$env_dir"
umask 077
cat >"$env_file" <<EOF
DOMAIN=$DOMAIN
APP_IMAGE=$ECR_REPOSITORY_URL:$tag
AWS_REGION=$AWS_REGION
LOG_GROUP=$LOG_GROUP
NEWSDIGEST_STORAGE_S3BUCKET=$PDF_BUCKET
POSTGRES_PASSWORD=$postgres_password
DB_MIGRATION_PASSWORD=$migration_password
DB_APP_PASSWORD=$app_password
SPRING_MAIL_HOST=email-smtp.$AWS_REGION.amazonaws.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=$smtp_username
SPRING_MAIL_PASSWORD=$smtp_password
NEWSDIGEST_MAIL_FROM=digest@$DOMAIN
NEWSDIGEST_MAIL_OPERATOR=$operator
EOF
unset postgres_password migration_password app_password operator smtp smtp_username smtp_password

echo "Logging in to ECR"
aws ecr get-login-password | docker login --username AWS --password-stdin \
  "${ECR_REPOSITORY_URL%%/*}" >/dev/null

cd "$app_dir"
compose() {
  docker compose -f docker-compose.prod.yml -f docker-compose.aws.yml --env-file "$env_file" "$@"
}
compose pull --quiet
# Stops the old app container before the new one starts: never two schedulers (docs/11)
compose up -d --wait --wait-timeout 300 --remove-orphans

echo "Installing the backup timer"
install -m 0644 infra/server/newsdigest-backup.service infra/server/newsdigest-backup.timer \
  /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now newsdigest-backup.timer

echo "$tag" >/etc/newsdigest/current-tag
docker image prune -f >/dev/null
echo "Deployed $tag"
