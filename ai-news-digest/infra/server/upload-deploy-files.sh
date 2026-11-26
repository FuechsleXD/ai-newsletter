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

# Git Bash on Windows: the AWS CLI is a Windows program and needs a Windows path
# (MSYS_NO_PATHCONV=1 switches off the automatic conversion)
source_dir="$staging"
if command -v cygpath >/dev/null 2>&1; then
  source_dir="$(cygpath -w "$staging")"
fi

aws s3 sync "$source_dir" "s3://$bucket/current/" --delete --only-show-errors
echo "Uploaded deploy files to s3://$bucket/current/"
