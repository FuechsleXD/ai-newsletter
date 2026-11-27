# GitHub Actions log in with OIDC, never with AWS keys (docs/11-deployment-cicd.md).
# - terraform-plan: read only, for pull requests and main (infra.yml)
# - terraform-apply: only in the GitHub environment "prod" with manual approval
# - cd: push images to ECR, upload deploy files, run deploy.sh via SSM (roadmap step 12)

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

locals {
  # Subject with immutable IDs (current GitHub format) and, as before, without them
  github_repository_parts = split("/", var.github_repository)
  github_sub_prefixes = compact([
    "repo:${var.github_repository}",
    var.github_repository_ids == null ? "" : format(
      "repo:%s@%d/%s@%d",
      local.github_repository_parts[0], var.github_repository_ids.owner_id,
      local.github_repository_parts[1], var.github_repository_ids.repository_id,
    ),
  ])
  github_subjects = {
    plan = flatten([for prefix in local.github_sub_prefixes :
    ["${prefix}:pull_request", "${prefix}:ref:refs/heads/main"]])
    apply = [for prefix in local.github_sub_prefixes : "${prefix}:environment:prod"]
    cd    = [for prefix in local.github_sub_prefixes : "${prefix}:environment:prod"]
  }
}

data "aws_iam_policy_document" "github_assume" {
  for_each = local.github_subjects

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = each.value
    }
  }
}

# --- Plan: read everything except data and secret values ---

resource "aws_iam_role" "terraform_plan" {
  name                 = "${var.name_prefix}-terraform-plan"
  assume_role_policy   = data.aws_iam_policy_document.github_assume["plan"].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_plan_read" {
  role       = aws_iam_role.terraform_plan.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "terraform_plan" {
  statement {
    sid     = "StateRead"
    actions = ["s3:GetObject", "s3:ListBucket"]
    resources = [
      "arn:${local.partition}:s3:::${var.state_bucket}",
      "arn:${local.partition}:s3:::${var.state_bucket}/*",
    ]
  }
  statement {
    sid       = "StateLock"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:${local.partition}:s3:::${var.state_bucket}/*.tflock"]
  }
  statement {
    sid    = "NoDataOrSecretValues"
    effect = "Deny"
    actions = [
      "s3:GetObject",
      "secretsmanager:GetSecretValue",
    ]
    resources = [
      "${aws_s3_bucket.pdfs.arn}/*",
      "${aws_s3_bucket.backups.arn}/*",
      "arn:${local.partition}:secretsmanager:${var.region}:${local.account_id}:secret:${local.secret_prefix}/*",
    ]
  }
}

resource "aws_iam_role_policy" "terraform_plan" {
  name   = "state-and-deny-data"
  role   = aws_iam_role.terraform_plan.id
  policy = data.aws_iam_policy_document.terraform_plan.json
}

# --- Apply: administrator, but only after approval in the environment "prod" ---

resource "aws_iam_role" "terraform_apply" {
  name                 = "${var.name_prefix}-terraform-apply"
  assume_role_policy   = data.aws_iam_policy_document.github_assume["apply"].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_apply_admin" {
  role       = aws_iam_role.terraform_apply.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AdministratorAccess"
}

# --- CD (roadmap step 12) ---

resource "aws_iam_role" "cd" {
  name                 = "${var.name_prefix}-cd"
  assume_role_policy   = data.aws_iam_policy_document.github_assume["cd"].json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "cd" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
  statement {
    sid       = "DeployFiles"
    actions   = ["s3:PutObject", "s3:DeleteObject", "s3:GetObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.deploy.arn, "${aws_s3_bucket.deploy.arn}/*"]
  }
  statement {
    sid     = "RunDeploy"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_instance.server.arn,
      "arn:${local.partition}:ssm:${var.region}::document/AWS-RunShellScript",
    ]
  }
  statement {
    sid       = "DeployResult"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
  # Outside the run window CD starts the stopped instance and stops it again (step 16b)
  statement {
    sid       = "StartStopForDeploy"
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [aws_instance.server.arn]
  }
  statement {
    sid       = "InstanceState"
    actions   = ["ec2:DescribeInstances", "ssm:DescribeInstanceInformation"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "cd" {
  name   = "deploy"
  role   = aws_iam_role.cd.id
  policy = data.aws_iam_policy_document.cd.json
}
