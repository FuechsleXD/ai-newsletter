#!/usr/bin/env bash
# Uploads the files the server needs for a release to s3://<deploy-bucket>/current/
# (run from ai-news-digest/ with AWS credentials; later by cd.yml, roadmap step 12).
#   infra/server/upload-deploy-files.sh <deploy-bucket>
set -euo pipefail

bucket="${1:?usage: infra/server/upload-deploy-files.sh <deploy-bucket>}"
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT

mkdir -p "$staging/docker" "$staging/infra"
cp docker-compose.prod.yml docker-compose.aws.yml "$staging/"
cp -r docker/caddy docker/postgres "$staging/docker/"
cp -r infra/server "$staging/infra/"

aws s3 sync "$staging/" "s3://$bucket/current/" --delete --only-show-errors
echo "Uploaded deploy files to s3://$bucket/current/"
