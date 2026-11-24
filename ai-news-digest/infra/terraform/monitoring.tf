# Logs, alarms, snapshots and the cost budget (docs/11-deployment-cicd.md).
# App metrics and their alarms follow in roadmap step 18.

locals {
  metric_namespace = "NewsDigest"
}

resource "aws_cloudwatch_log_group" "containers" {
  name              = "/newsdigest/prod"
  retention_in_days = var.log_retention_days
}

resource "aws_sns_topic" "alarms" {
  name = "${var.name_prefix}-alarms"
}

# Must be confirmed once via the link in the mail
resource "aws_sns_topic_subscription" "alarms_mail" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# Hardware or host problem: AWS moves the instance to a healthy host (same IP and volumes)
resource "aws_cloudwatch_metric_alarm" "system_check" {
  alarm_name          = "${var.name_prefix}-system-check"
  alarm_description   = "System status check failed; the instance is recovered automatically"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions = [
    "arn:${local.partition}:automate:${var.region}:ec2:recover",
    aws_sns_topic.alarms.arn,
  ]
}

resource "aws_cloudwatch_metric_alarm" "instance_check" {
  alarm_name          = "${var.name_prefix}-instance-check"
  alarm_description   = "Instance status check failed (operating system)"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_Instance"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# backup.sh reports every successful dump; no report within a day is an alarm
resource "aws_cloudwatch_metric_alarm" "backup_missing" {
  alarm_name          = "${var.name_prefix}-backup-missing"
  alarm_description   = "No successful database backup within the last 24 hours (backup.sh)"
  namespace           = local.metric_namespace
  metric_name         = "BackupSucceeded"
  statistic           = "Sum"
  period              = 86400
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

# --- Daily EBS snapshots of the data volume (Data Lifecycle Manager) ---

data "aws_iam_policy_document" "dlm_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["dlm.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "dlm" {
  name               = "${var.name_prefix}-dlm"
  assume_role_policy = data.aws_iam_policy_document.dlm_assume.json
}

resource "aws_iam_role_policy_attachment" "dlm" {
  role       = aws_iam_role.dlm.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}

resource "aws_dlm_lifecycle_policy" "data_volume" {
  description        = "Daily snapshots of the data volume"
  execution_role_arn = aws_iam_role.dlm.arn
  state              = "ENABLED"

  policy_details {
    resource_types = ["VOLUME"]
    target_tags    = { Backup = "${var.name_prefix}-daily" }

    schedule {
      name      = "daily"
      copy_tags = true

      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["02:00"]
      }

      retain_rule {
        count = var.snapshot_retention_count
      }
    }
  }
}

# --- AWS costs; the AI costs are limited separately by the budget of the app ---

resource "aws_budgets_budget" "monthly" {
  name         = "${var.name_prefix}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alarm_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alarm_email]
  }
}
