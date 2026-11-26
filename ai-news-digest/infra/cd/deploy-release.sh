#!/usr/bin/env bash
# Starts deploy.sh for a release on the server via SSM and waits for it (cd.yml; also usable by
# hand from ai-news-digest/ with AWS credentials, docs/betrieb/deployment.md).
#   infra/cd/deploy-release.sh <instance-id> <deploy-bucket> <image-tag>
# deploy.sh comes from releases/<tag>/ if that release was uploaded with its tag, otherwise from
# current/ (releases deployed by hand before roadmap step 12).
set -euo pipefail

instance="${1:?usage: infra/cd/deploy-release.sh <instance-id> <deploy-bucket> <image-tag>}"
bucket="${2:?usage: infra/cd/deploy-release.sh <instance-id> <deploy-bucket> <image-tag>}"
tag="${3:?usage: infra/cd/deploy-release.sh <instance-id> <deploy-bucket> <image-tag>}"
[[ "$tag" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Invalid image tag: $tag" >&2; exit 1; }

script="s3://$bucket/releases/$tag/infra/server/deploy.sh"
if ! aws s3 ls "$script" >/dev/null 2>&1; then
  script="s3://$bucket/current/infra/server/deploy.sh"
fi
echo "Deploying $tag with $script"

"$(dirname "$0")/../server/run-on-server.sh" "$instance" \
  "aws s3 cp $script /tmp/deploy.sh --only-show-errors" \
  "bash /tmp/deploy.sh $tag"
