#!/usr/bin/env bash
# Uploads the files the server needs for a release (run from ai-news-digest/ with AWS credentials;
# by hand per docs/betrieb/aws-bootstrap.md or by cd.yml).
#   infra/server/upload-deploy-files.sh <deploy-bucket> [<image-tag>]
# Always to s3://<deploy-bucket>/current/; with a tag also to releases/<tag>/, so that a rollback
# to this release later gets the deploy files that belong to its image (deploy.sh).
set -euo pipefail

bucket="${1:?usage: infra/server/upload-deploy-files.sh <deploy-bucket> [<image-tag>]}"
tag="${2:-}"
if [[ -n "$tag" && ! "$tag" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid image tag: $tag" >&2
  exit 1
fi
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

targets=(current)
if [[ -n "$tag" ]]; then
  targets+=("releases/$tag")
fi
for target in "${targets[@]}"; do
  aws s3 sync "$source_dir" "s3://$bucket/$target/" --delete --only-show-errors
  echo "Uploaded deploy files to s3://$bucket/$target/"
done
