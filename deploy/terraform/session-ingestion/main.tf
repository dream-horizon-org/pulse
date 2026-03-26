terraform {
  required_version = ">= 1.3.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket       = "pulse-deployment-config"
    key          = "terraform/production/session-ingestion/terraform.tfstate"
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

# Session replay ingestion is a Kafka consumer (no inbound HTTP). Pattern matches
# deploy/terraform/otel-collector-2: ASG + launch template, no NLB/Route53.

resource "aws_launch_template" "ingestion" {
  name_prefix   = "pulse-session-replay-ingestion-"
  image_id      = var.ami_id
  instance_type = var.instance_type

  key_name = var.ssh_key_name

  vpc_security_group_ids = var.vpc_security_group_ids

  instance_market_options {
    market_type = "spot"
  }

  iam_instance_profile {
    name = var.instance_profile_name
  }

  user_data = base64encode(templatefile("${path.module}/user-data.sh", {
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

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.root_volume_size_gb
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  lifecycle {
    create_before_destroy = true
  }

  tag_specifications {
    resource_type = "instance"

    tags = {
      Role    = "pulse-session-replay-ingestion"
      service = "pulse"
    }
  }

  tag_specifications {
    resource_type = "volume"

    tags = {
      Role       = "pulse-session-replay-ingestion"
      service    = "pulse"
      VolumeRole = "root"
      ManagedBy  = "terraform"
    }
  }

  metadata_options {
    http_tokens = "required"
  }
}

resource "aws_autoscaling_group" "ingestion" {
  name                      = "pulse-session-replay-ingestion-asg"
  max_size                  = var.ingestion_count
  min_size                  = var.ingestion_count
  desired_capacity          = var.ingestion_count
  vpc_zone_identifier       = var.private_subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = var.health_check_grace_period_seconds

  launch_template {
    id      = aws_launch_template.ingestion.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-session-replay-ingestion"
    propagate_at_launch = true
  }

  tag {
    key                 = "service"
    value               = "pulse"
    propagate_at_launch = true
  }

  lifecycle {
    create_before_destroy = true
  }
}
