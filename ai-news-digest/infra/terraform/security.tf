# Instance role with minimal rights and the (empty) secrets. Secret values are never set by
# Terraform, so they are neither in the state nor in the repo (ADR-011, docs/06-security.md).

locals {
  secret_prefix = "newsdigest/prod"
  # Runtime secrets are read by the app, start secrets by deploy.sh (ADR-015)
  secrets = {
    "ai/anthropic"  = "API key of Anthropic (read by the app, rotatable without restart)"
    "db/postgres"   = "Password of the PostgreSQL superuser (administration and init only)"
    "db/migration"  = "Password of newsdigest_migration (Flyway)"
    "db/app"        = "Password of newsdigest_app (data access)"
    "mail/smtp"     = "SES SMTP credentials as JSON {\"username\": ..., \"password\": ...}"
    "mail/operator" = "Mail address of the operator for error mails"
  }
}

resource "aws_secretsmanager_secret" "app" {
  for_each = local.secrets

  name                    = "${local.secret_prefix}/${each.key}"
  description             = each.value
  recovery_window_in_days = 7
}

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "server" {
  name               = "${var.name_prefix}-server"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_instance_profile" "server" {
  name = "${var.name_prefix}-server"
  role = aws_iam_role.server.name
}

# Session Manager instead of SSH; Patch Manager for OS updates
resource "aws_iam_role_policy_attachment" "server_ssm" {
  role       = aws_iam_role.server.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "server" {
  statement {
    sid       = "PdfObjects"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.pdfs.arn}/*"]
  }
  statement {
    sid       = "PdfBucketList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.pdfs.arn]
  }
  statement {
    sid       = "BackupUpload"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.backups.arn}/*"]
  }
  statement {
    sid       = "DeployFilesRead"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.deploy.arn, "${aws_s3_bucket.deploy.arn}/*"]
  }
  statement {
    sid       = "SecretsRead"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = ["arn:${local.partition}:secretsmanager:${var.region}:${local.account_id}:secret:${local.secret_prefix}/*"]
  }
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
  statement {
    sid       = "ContainerLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.containers.arn}:*"]
  }
  statement {
    sid       = "BackupMetric"
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = [local.metric_namespace]
    }
  }
}

resource "aws_iam_role_policy" "server" {
  name   = "newsdigest"
  role   = aws_iam_role.server.id
  policy = data.aws_iam_policy_document.server.json
}
