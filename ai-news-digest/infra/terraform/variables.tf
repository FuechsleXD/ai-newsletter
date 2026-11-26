variable "region" {
  description = "AWS region of all resources (ADR-015)."
  type        = string
  default     = "eu-central-1"
}

variable "name_prefix" {
  description = "Prefix of resource names."
  type        = string
  default     = "newsdigest-prod"
}

variable "domain_name" {
  description = "Public domain; registered in Route 53, which also creates the hosted zone."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type (ARM/Graviton, ADR-015)."
  type        = string
  default     = "t4g.medium"
}

variable "root_volume_gb" {
  description = "Size of the root volume (operating system)."
  type        = number
  default     = 20
}

variable "data_volume_gb" {
  description = "Size of the data volume mounted as /var/lib/docker (database, certificates, images)."
  type        = number
  default     = 30
}

variable "github_repository" {
  description = "GitHub repository (owner/name) allowed to assume the OIDC roles."
  type        = string
}

variable "github_repository_ids" {
  description = <<-EOT
    Numeric IDs of the GitHub owner and repository. GitHub writes them into the OIDC subject
    ("repo:<owner>@<owner_id>/<name>@<repo_id>:..."), so a renamed or re-created repository with
    the same name cannot assume the roles. Not secret; shown in the CloudTrail event of a failed
    login. Null: only the subject without IDs.
  EOT
  type = object({
    owner_id      = number
    repository_id = number
  })
  default = null
}

variable "state_bucket" {
  description = "S3 bucket of the Terraform state (backend.hcl)."
  type        = string
}

variable "monthly_budget_usd" {
  description = "Monthly AWS cost budget; notifications at 80 % actual and 100 % forecast."
  type        = number
  default     = 40
}

variable "alarm_email" {
  description = "Mail address for alarms and budget notifications. Never in the repo: TF_VAR_alarm_email locally, GitHub secret ALARM_EMAIL in the CI."
  type        = string
  sensitive   = true
}

variable "log_retention_days" {
  description = "Retention of the container logs in CloudWatch (docs/12-defaults.md)."
  type        = number
  default     = 30
}

variable "pdf_retention_days" {
  description = "Retention of the PDFs in S3 (docs/12-defaults.md: 2 years)."
  type        = number
  default     = 730
}

variable "backup_retention_days" {
  description = "Retention of the database dumps in S3 (docs/12-defaults.md)."
  type        = number
  default     = 30
}

variable "snapshot_retention_count" {
  description = "Number of daily EBS snapshots of the data volume that are kept."
  type        = number
  default     = 7
}

variable "compose_version" {
  description = "Docker Compose plugin installed on the instance (pinned, with checksum)."
  type        = string
  default     = "v5.7.0"
}

variable "compose_sha256" {
  description = "SHA-256 of docker-compose-linux-aarch64 of compose_version (checksums.txt of the release)."
  type        = string
  default     = "15861535d14e26555736b49376c95e14bfd94285366c8446bfac7156a972e76f"
}
