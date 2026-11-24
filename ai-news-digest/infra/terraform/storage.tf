# Private S3 buckets (PDFs, database dumps, deploy files) and the ECR repository of the app image.
# Buckets with data are protected against accidental deletion.

locals {
  buckets = {
    pdfs    = "${var.name_prefix}-pdfs-${local.account_id}"
    backups = "${var.name_prefix}-backups-${local.account_id}"
    deploy  = "${var.name_prefix}-deploy-${local.account_id}"
  }
}

resource "aws_s3_bucket" "pdfs" {
  bucket = local.buckets.pdfs

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket" "backups" {
  bucket = local.buckets.backups

  lifecycle {
    prevent_destroy = true
  }
}

# Compose files, Caddyfile and server scripts of the current release (deploy.sh)
resource "aws_s3_bucket" "deploy" {
  bucket = local.buckets.deploy
}

locals {
  all_buckets = {
    pdfs    = aws_s3_bucket.pdfs
    backups = aws_s3_bucket.backups
    deploy  = aws_s3_bucket.deploy
  }
}

resource "aws_s3_bucket_public_access_block" "all" {
  for_each = local.all_buckets

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "all" {
  for_each = local.all_buckets

  bucket = each.value.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "all" {
  for_each = local.all_buckets

  bucket = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "all" {
  for_each = local.all_buckets

  bucket = each.value.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Only TLS access
data "aws_iam_policy_document" "tls_only" {
  for_each = local.all_buckets

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      each.value.arn,
      "${each.value.arn}/*",
    ]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "tls_only" {
  for_each = local.all_buckets

  bucket = each.value.id
  policy = data.aws_iam_policy_document.tls_only[each.key].json

  depends_on = [aws_s3_bucket_public_access_block.all]
}

resource "aws_s3_bucket_lifecycle_configuration" "pdfs" {
  bucket = aws_s3_bucket.pdfs.id

  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    expiration {
      days = var.pdf_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    expiration {
      days = var.backup_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "deploy" {
  bucket = aws_s3_bucket.deploy.id

  rule {
    id     = "old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

resource "aws_ecr_repository" "app" {
  name                 = "newsdigest"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 30 images (rollback to earlier tags stays possible)"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = { type = "expire" }
    }]
  })
}
