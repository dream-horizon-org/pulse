terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      service = "pulse"
    }
  }
}

resource "random_uuid" "kraft_cluster" {}

locals {
  controller_names = [for i in range(var.num_controllers) : format("pulse-kafka-controller-%02d", i + 1)]
  broker_names     = [for i in range(var.num_brokers) : format("pulse-kafka-broker-%02d", i + 1)]

  # gp3 knobs only when type=gp3, else null
  ebs_iops       = var.kafka_ebs_type == "gp3" ? var.kafka_ebs_iops : null
  ebs_throughput = var.kafka_ebs_type == "gp3" ? var.kafka_ebs_throughput : null
}

# -------------------------
# Controllers (ROOT disk only)
# -------------------------
resource "aws_instance" "controller" {
  count         = var.num_controllers
  ami           = var.ami_id
  instance_type = var.instance_type
  associate_public_ip_address = false

  subnet_id              = var.private_subnet_ids[count.index % length(var.private_subnet_ids)]
  vpc_security_group_ids = var.vpc_security_group_ids
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile

  metadata_options {
    http_tokens = "required"
  }

  user_data = templatefile("${path.module}/user-data.sh", {
    role              = "controller"
    node_id           = count.index + 1
    num_controllers   = var.num_controllers
    num_brokers       = var.num_brokers
    kraft_cluster_id  = random_uuid.kraft_cluster.result
    kafka_data_dir    = var.kafka_data_dir
    route53_zone_name = var.route53_zone_name
  })

  tags = {
    Name    = local.controller_names[count.index]
    Role    = "kafka-controller"
    service = "pulse"
  }
}

# -------------------------
# Brokers (secondary EBS disk)
# -------------------------
resource "aws_instance" "broker" {
  count         = var.num_brokers
  ami           = var.ami_id
  instance_type = var.instance_type
  associate_public_ip_address = false

  subnet_id              = var.private_subnet_ids[count.index % length(var.private_subnet_ids)]
  vpc_security_group_ids = var.vpc_security_group_ids
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile

  metadata_options {
    http_tokens = "required"
  }

  # Dedicated data disk ONLY for brokers
  ebs_block_device {
    device_name           = "/dev/sdf"
    volume_size           = var.kafka_ebs_size_gb
    volume_type           = var.kafka_ebs_type
    encrypted             = var.kafka_ebs_encrypted
    iops                  = local.ebs_iops
    throughput            = local.ebs_throughput
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/user-data.sh", {
    role              = "broker"
    node_id           = var.num_controllers + (count.index + 1)
    num_controllers   = var.num_controllers
    num_brokers       = var.num_brokers
    kraft_cluster_id  = random_uuid.kraft_cluster.result
    kafka_data_dir    = var.kafka_data_dir
    route53_zone_name = var.route53_zone_name
  })

  tags = {
    Name    = local.broker_names[count.index]
    Role    = "kafka-broker"
    service = "pulse"
  }
}

# -------------------------
# Route53 private A records
# NOTE: aws_route53_record does NOT support tags.
# -------------------------
resource "aws_route53_record" "controller_a" {
  count   = var.num_controllers
  zone_id = var.route53_zone_id
  name    = "${local.controller_names[count.index]}.${var.route53_zone_name}"
  type    = "A"
  ttl     = 30
  records = [aws_instance.controller[count.index].private_ip]
}

resource "aws_route53_record" "broker_a" {
  count   = var.num_brokers
  zone_id = var.route53_zone_id
  name    = "${local.broker_names[count.index]}.${var.route53_zone_name}"
  type    = "A"
  ttl     = 30
  records = [aws_instance.broker[count.index].private_ip]
}

# -------------------------
# Outputs
# -------------------------
output "kraft_cluster_id" {
  value = random_uuid.kraft_cluster.result
}

output "controller_dns" {
  value = aws_route53_record.controller_a[*].fqdn
}

output "broker_dns" {
  value = aws_route53_record.broker_a[*].fqdn
}

output "controller_private_ips" {
  value = aws_instance.controller[*].private_ip
}

output "broker_private_ips" {
  value = aws_instance.broker[*].private_ip
}
