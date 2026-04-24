terraform {
  required_version = ">= 1.3.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket       = "pulse-deployment-config"
    key          = "terraform/production/session-replay-ingestion/terraform.tfstate"
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
  component_name = "pulse-session-replay-ingestion"
  tag_base = {
    org_name         = "horizon"
    environment_name = "production"
    component_name   = local.component_name
    component_type   = "application"
    service_name     = "pulse"
  }
}

# Kafka consumer only — no NLB/ALB (pulse-server-style LT + ASG otherwise).

resource "aws_launch_template" "ingestion" {
  name = "pulse-session-replay-ingestion-lt"

  tags = merge(local.tag_base, {
    Name          = "pulse-session-replay-ingestion-lt"
    resource_type = "lt"
  })

  image_id               = var.ami_id
  key_name               = var.ssh_key_name
  vpc_security_group_ids = var.ec2_security_group_ids

  iam_instance_profile {
    name = var.instance_profile_name
  }

  private_dns_name_options {
    enable_resource_name_dns_a_record = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  user_data = base64encode(templatefile("${path.module}/user-data.sh", {
    artifact_version     = var.artifact_version
    kafka_brokers        = var.kafka_brokers
    kafka_topic          = var.kafka_topic
    kafka_metadata_topic = var.kafka_metadata_topic
    kafka_group_id       = var.kafka_group_id
    s3_endpoint          = var.s3_endpoint
    s3_bucket            = var.s3_bucket
    s3_region            = var.s3_region
    s3_prefix            = var.s3_prefix
    max_batch_size_kb    = var.max_batch_size_kb
    max_batch_age_ms     = var.max_batch_age_ms
    fetch_batch_size     = var.fetch_batch_size
    s3_timeout_ms        = var.s3_timeout_ms
  }))

  tag_specifications {
    resource_type = "instance"

    tags = merge(local.tag_base, {
      Name          = "pulse-session-replay-ingestion-instance"
      resource_type = "ec2"
    })
  }

  tag_specifications {
    resource_type = "volume"

    tags = merge(local.tag_base, {
      Name          = "pulse-session-replay-ingestion-volume"
      resource_type = "ebs"
    })
  }

  lifecycle {
    create_before_destroy = true
  }
}

# No load_balancers / target_group_arns: Kafka consumers — suppress generic ASG+ELB IaC rules.
resource "aws_autoscaling_group" "ingestion" {
  name = "pulse-session-replay-ingestion-asg"

  vpc_zone_identifier       = var.ec2_subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = var.health_check_grace_period_seconds
  desired_capacity          = var.desired_capacity
  min_size                  = var.asg_min_size
  max_size                  = var.asg_max_size
  protect_from_scale_in     = true

  mixed_instances_policy {
    instances_distribution {
      on_demand_percentage_above_base_capacity = 0
      on_demand_base_capacity                  = var.asg_on_demand_base_capacity
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.ingestion.id
        version            = aws_launch_template.ingestion.latest_version
      }

      dynamic "override" {
        for_each = var.instance_types
        content {
          instance_type = override.value
        }
      }
    }
  }

  instance_refresh {
    strategy = "Rolling"
    triggers = ["launch_template"]
    preferences {
      min_healthy_percentage       = 100
      scale_in_protected_instances = "Refresh"
    }
  }

  tag {
    key                 = "Name"
    value               = "pulse-session-replay-ingestion-asg"
    propagate_at_launch = false
  }
  tag {
    key                 = "org_name"
    value               = local.tag_base.org_name
    propagate_at_launch = false
  }
  tag {
    key                 = "environment_name"
    value               = local.tag_base.environment_name
    propagate_at_launch = false
  }
  tag {
    key                 = "component_name"
    value               = local.tag_base.component_name
    propagate_at_launch = false
  }
  tag {
    key                 = "component_type"
    value               = local.tag_base.component_type
    propagate_at_launch = false
  }
  tag {
    key                 = "service_name"
    value               = local.tag_base.service_name
    propagate_at_launch = false
  }
  tag {
    key                 = "resource_type"
    value               = "ec2"
    propagate_at_launch = false
  }

  lifecycle {
    create_before_destroy = true
  }
}
