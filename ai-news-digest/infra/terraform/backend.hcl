# Partial backend configuration: terraform init -backend-config=backend.hcl
# Not secret; the bucket is private, versioned and encrypted (docs/betrieb/aws-bootstrap.md).
bucket       = "fuechslexd-newsdigest-tfstate"
key          = "prod/terraform.tfstate"
region       = "eu-central-1"
encrypt      = true
use_lockfile = true
