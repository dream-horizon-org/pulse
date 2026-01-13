terraform {
  required_version = ">= 1.3.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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

# -------------------------------
# Launch template for OTEL collectors
# -------------------------------
resource "aws_launch_template" "otel" {
  name_prefix   = "pulse-otel-collector-2-"
  image_id      = var.ami_id
  instance_type = var.instance_type

  # Optional SSH access
  key_name = var.key_name

  vpc_security_group_ids = var.vpc_security_group_ids

  instance_market_options {
    market_type = "spot"
  }

  dynamic "iam_instance_profile" {
    for_each = var.iam_instance_profile == null ? [] : [1]
    content {
      name = var.iam_instance_profile
    }
  }


  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = 20
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
      Role = "pulse-otel-collector-2"
      service = "pulse"
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name      = "pulse-otel-collector-volume"
      service   = "pulse"
      ManagedBy = "terraform"
    }
  }

   metadata_options {
      http_tokens = "required"
    }
}

# -------------------------------
# Auto Scaling Group for collectors
# -------------------------------
resource "aws_autoscaling_group" "otel" {
  name                      = "pulse-otel-collector-2-asg"
  max_size                  = var.collector_count
  min_size                  = var.collector_count
  desired_capacity          = var.collector_count
  vpc_zone_identifier       = var.subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = 60

  launch_template {
    id      = aws_launch_template.otel.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-otel-collector-2"
    propagate_at_launch = true
  }

  tag {
      key                 = "service"
      value               = "pulse"
      propagate_at_launch = true
    }
}

