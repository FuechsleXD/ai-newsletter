# No secrets and no mail addresses in the outputs.

output "instance_id" {
  description = "EC2 instance (SSM session: aws ssm start-session --target <id>)."
  value       = aws_instance.server.id
}

output "public_ip" {
  description = "Elastic IP of the server."
  value       = aws_eip.server.public_ip
}

output "ecr_repository_url" {
  description = "ECR repository of the app image."
  value       = aws_ecr_repository.app.repository_url
}

output "buckets" {
  description = "S3 buckets (PDFs, database dumps, deploy files)."
  value = {
    pdfs    = aws_s3_bucket.pdfs.bucket
    backups = aws_s3_bucket.backups.bucket
    deploy  = aws_s3_bucket.deploy.bucket
  }
}

output "secret_names" {
  description = "Secrets whose values are entered by hand (docs/betrieb/aws-bootstrap.md)."
  value       = [for secret in aws_secretsmanager_secret.app : secret.name]
}

output "github_role_arns" {
  description = "Roles for the GitHub variables AWS_PLAN_ROLE_ARN, AWS_APPLY_ROLE_ARN and AWS_CD_ROLE_ARN."
  value = {
    plan  = aws_iam_role.terraform_plan.arn
    apply = aws_iam_role.terraform_apply.arn
    cd    = aws_iam_role.cd.arn
  }
}

output "log_group" {
  description = "CloudWatch log group of the containers."
  value       = aws_cloudwatch_log_group.containers.name
}
