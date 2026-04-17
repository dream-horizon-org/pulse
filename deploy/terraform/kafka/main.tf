terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
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

locals {
  # pulse-kafka-01, pulse-kafka-02, ...
  broker_names = [for i in range(var.num_brokers) : format("pulse-kafka-%02d", i + 1)]
}

# -------------------------------------------------------------------
# Data — look up each subnet so we can place EBS volumes in the
# same AZ as their broker instance.
# -------------------------------------------------------------------
data "aws_subnet" "broker" {
  count = length(var.private_subnet_ids)
  id    = var.private_subnet_ids[count.index]
}

# -------------------------------------------------------------------
# Launch Template — no user_data; Kafka is pre-installed in the AMI.
# Ansible configures each broker after provisioning.
# -------------------------------------------------------------------
resource "aws_launch_template" "kafka_broker" {
  name          = "pulse-kafka-broker"
  image_id      = var.ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  iam_instance_profile {
    name = var.iam_instance_profile
  }

  network_interfaces {
    associate_public_ip_address = false
    security_groups             = var.vpc_security_group_ids
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tag_specifications {
    resource_type = "instance"
    tags = {
      Role             = "kafka-broker"
      org_name         = "horizon"
      environment_name = "production"
      component_name   = "pulse-kafka"
      component_type   = "data"
      service_name     = "pulse"
      resource_type    = "ec2"
    }
  }
}

# -------------------------------------------------------------------
# EBS Data Volumes — one per broker, lifecycle-protected.
# Kept alive even when the EC2 instance is replaced or terminated.
# -------------------------------------------------------------------
resource "aws_ebs_volume" "broker_data" {
  count             = var.num_brokers
  availability_zone = data.aws_subnet.broker[count.index % length(var.private_subnet_ids)].availability_zone
  size              = var.ebs_size_gb
  type              = var.ebs_type
  encrypted         = var.ebs_encrypted
  iops              = var.ebs_type == "gp3" ? var.ebs_iops : null
  throughput        = var.ebs_type == "gp3" ? var.ebs_throughput : null

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name             = "${local.broker_names[count.index]}-data"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-kafka"
    resource_type    = "ebs"
  }
}

# -------------------------------------------------------------------
# EC2 Instances — created from Launch Template, one per broker.
# ignore_changes on launch_template lets you update the LT without
# forcing instance replacement (replacement is done via Ansible/Jenkins).
# -------------------------------------------------------------------
resource "aws_instance" "broker" {
  count     = var.num_brokers
  subnet_id = var.private_subnet_ids[count.index % length(var.private_subnet_ids)]

  launch_template {
    id      = aws_launch_template.kafka_broker.id
    version = "$Latest"
  }

  lifecycle {
    ignore_changes = [launch_template]
  }

  tags = {
    Name = local.broker_names[count.index]
  }
}

# -------------------------------------------------------------------
# Attach EBS data volumes to broker instances
# -------------------------------------------------------------------
resource "aws_volume_attachment" "broker_data" {
  count       = var.num_brokers
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.broker_data[count.index].id
  instance_id = aws_instance.broker[count.index].id
  # Do not detach on destroy — the EBS volume is independently lifecycle-managed
  stop_instance_before_detaching = false
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
output "broker_dns" {
  value       = aws_route53_record.broker_a[*].fqdn
  description = "Broker DNS hostnames — use these in Ansible inventory"
}

output "broker_private_ips" {
  value       = aws_instance.broker[*].private_ip
  description = "Broker private IPs"
}

output "bootstrap_servers" {
  value       = join(",", [for fqdn in aws_route53_record.broker_a[*].fqdn : "${fqdn}:9092"])
  description = "Kafka bootstrap servers string — use this in client configs"
}

output "launch_template_id" {
  value       = aws_launch_template.kafka_broker.id
  description = "Launch Template ID — reference this in the Jenkins replace-broker job"
}

output "ebs_volume_ids" {
  value       = aws_ebs_volume.broker_data[*].id
  description = "EBS data volume IDs — one per broker, persist across instance replacements"
}
