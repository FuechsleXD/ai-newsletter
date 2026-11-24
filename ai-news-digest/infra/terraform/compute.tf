# One EC2 instance with Docker Compose (ADR-005). All Docker data (database, certificates, images)
# lives on a separate encrypted EBS volume mounted as /var/lib/docker; it is snapshotted daily (DLM).

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "server" {
  ami                     = data.aws_ssm_parameter.al2023_arm64.insecure_value
  instance_type           = var.instance_type
  subnet_id               = aws_subnet.public.id
  vpc_security_group_ids  = [aws_security_group.server.id]
  iam_instance_profile    = aws_iam_instance_profile.server.name
  disable_api_termination = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
    # 2 hops: the app container reaches the instance role through the Docker bridge
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    data_volume_id     = aws_ebs_volume.data.id
    compose_version    = var.compose_version
    compose_sha256     = var.compose_sha256
    region             = var.region
    domain             = var.domain_name
    deploy_bucket      = aws_s3_bucket.deploy.bucket
    backup_bucket      = aws_s3_bucket.backups.bucket
    pdf_bucket         = aws_s3_bucket.pdfs.bucket
    ecr_repository_url = aws_ecr_repository.app.repository_url
    log_group          = aws_cloudwatch_log_group.containers.name
  })

  tags = { Name = var.name_prefix }

  lifecycle {
    # A new AMI or changed bootstrap script must not replace the running server; OS updates come
    # from Patch Manager, a replacement is a deliberate decision (taint)
    ignore_changes = [ami, user_data]
  }
}

resource "aws_ebs_volume" "data" {
  availability_zone = aws_subnet.public.availability_zone
  type              = "gp3"
  size              = var.data_volume_gb
  encrypted         = true

  tags = {
    Name   = "${var.name_prefix}-data"
    Backup = "${var.name_prefix}-daily"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.data.id
  instance_id = aws_instance.server.id
}

resource "aws_eip" "server" {
  domain   = "vpc"
  instance = aws_instance.server.id

  tags = { Name = var.name_prefix }
}
