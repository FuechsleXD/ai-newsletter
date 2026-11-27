# Run window (roadmap step 16b, ADR-019): EventBridge Scheduler starts the instance shortly before
# the scheduled runs and stops it afterwards, so it does not run all week. Off with
# run_window = null; the instance then runs all the time.
# The app makes up a start missed by up to 6 hours and cancels runs after 3 hours; backups run
# 10 minutes after boot and before shutdown (infra/server/newsdigest-backup*).

locals {
  run_window_enabled = var.run_window != null
  instance_actions = local.run_window_enabled ? {
    start = { cron = var.run_window.start, action = "startInstances" }
    stop  = { cron = var.run_window.stop, action = "stopInstances" }
  } : {}
}

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  count              = local.run_window_enabled ? 1 : 0
  name               = "${var.name_prefix}-run-window"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

# Only this instance, only start and stop. The volumes use the AWS managed EBS key, which needs
# no KMS permissions here.
data "aws_iam_policy_document" "scheduler" {
  statement {
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [aws_instance.server.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  count  = local.run_window_enabled ? 1 : 0
  name   = "start-stop-instance"
  role   = aws_iam_role.scheduler[0].id
  policy = data.aws_iam_policy_document.scheduler.json
}

resource "aws_scheduler_schedule" "instance" {
  for_each = local.instance_actions

  name                         = "${var.name_prefix}-${each.key}"
  description                  = "${title(each.key)} the server for the run window (docs/betrieb/zeitfenster.md)"
  schedule_expression          = "cron(${each.value.cron})"
  schedule_expression_timezone = var.run_window.timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    # Universal target: calls the EC2 API directly, no Lambda needed
    arn      = "arn:${local.partition}:scheduler:::aws-sdk:ec2:${each.value.action}"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.server.id] })

    retry_policy {
      maximum_event_age_in_seconds = 900
      maximum_retry_attempts       = 5
    }
  }
}
