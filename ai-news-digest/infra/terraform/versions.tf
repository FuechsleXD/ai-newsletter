# Infrastructure of AI News Digest on AWS (docs/11-deployment-cicd.md, ADR-011, ADR-015).
terraform {
  required_version = ">= 1.17.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.73.0"
    }
  }

  # Remote state in S3 with native locking; bucket and key in backend.hcl.
  # The bucket is created once by hand (docs/betrieb/aws-bootstrap.md).
  backend "s3" {}
}
