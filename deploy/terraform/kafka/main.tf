terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }

  backend "s3" {
    bucket       = "pulse-deployment-config"
    key          = "terraform/production/kafka/terraform.tfstate"
    region       = "ap-south-1"
    use_lockfile = true
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

# One cluster UUID shared by all nodes — stays stable across applies
resource "random_uuid" "kraft_cluster" {}

locals {
  # pulse-kafka-01, pulse-kafka-02, ...
  broker_names   = [for i in range(var.num_brokers) : format("pulse-kafka-%02d", i + 1)]
  retention_ms   = var.retention_hours * 3600000
  ebs_iops       = var.ebs_type == "gp3" ? var.ebs_iops : null
  ebs_throughput = var.ebs_type == "gp3" ? var.ebs_throughput : null
}

# -------------------------------------------------------------------
# EC2 Instances — KRaft combined mode (broker + controller on each node)
# -------------------------------------------------------------------
resource "aws_instance" "broker" {
  count         = var.num_brokers
  ami           = var.ami_id
  instance_type = var.instance_type

  associate_public_ip_address = false
  subnet_id                   = var.private_subnet_ids[count.index % length(var.private_subnet_ids)]
  vpc_security_group_ids      = var.vpc_security_group_ids
  key_name                    = var.key_name
  iam_instance_profile        = var.iam_instance_profile

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  # Dedicated data disk for Kafka logs (separate from root OS disk)
  ebs_block_device {
    device_name           = "/dev/sdf"
    volume_size           = var.ebs_size_gb
    volume_type           = var.ebs_type
    encrypted             = var.ebs_encrypted
    iops                  = local.ebs_iops
    throughput            = local.ebs_throughput
    delete_on_termination = true
  }

  user_data_base64 = base64encode(templatefile("${path.module}/user-data.sh", {
    node_id             = count.index + 1
    num_brokers         = var.num_brokers
    kraft_cluster_id    = random_uuid.kraft_cluster.result
    kafka_version       = var.kafka_version
    kafka_data_dir      = var.kafka_data_dir
    route53_zone_name   = var.route53_zone_name
    replication_factor  = var.replication_factor
    min_insync_replicas = var.min_insync_replicas
    retention_ms        = local.retention_ms
    compression_type    = var.compression_type
    kafka_topics        = var.kafka_topics
  }))

  tags = {
    Name             = local.broker_names[count.index]
    Role             = "kafka-broker"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-kafka"
    component_type   = "data"
    service_name     = "pulse"
    resource_type    = "ec2"
  }
}

# -------------------------------------------------------------------
# Route53 private A records (stable DNS for broker discovery)
# -------------------------------------------------------------------
resource "aws_route53_record" "broker_a" {
  count   = var.num_brokers
  zone_id = var.route53_zone_id
  name    = "${local.broker_names[count.index]}.${var.route53_zone_name}"
  type    = "A"
  ttl     = 30
  records = [aws_instance.broker[count.index].private_ip]
}

# -------------------------------------------------------------------
# Outputs
# -------------------------------------------------------------------
output "kraft_cluster_id" {
  value       = random_uuid.kraft_cluster.result
  description = "KRaft cluster UUID — keep this stable, do not recreate"
}

output "broker_dns" {
  value       = aws_route53_record.broker_a[*].fqdn
  description = "Broker DNS hostnames"
}

output "broker_private_ips" {
  value       = aws_instance.broker[*].private_ip
  description = "Broker private IPs"
}

output "bootstrap_servers" {
  value       = join(",", [for fqdn in aws_route53_record.broker_a[*].fqdn : "${fqdn}:9092"])
  description = "Kafka bootstrap servers string — use this in client configs"
}
