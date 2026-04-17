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
        key          = "terraform/production/pulse-otel-consumer/terraform.tfstate"
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

# -------------------------------
# Launch template for OTEL collectors
# -------------------------------
resource "aws_launch_template" "otel-consumer" {
  name_prefix   = "pulse-otel-consumer-lt-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  key_name = var.key_name

  iam_instance_profile {
      name = var.instance_profile_name
  }

  vpc_security_group_ids = var.vpc_security_group_ids

  lifecycle {
      create_before_destroy = true
  }

  tag_specifications {
      resource_type = "instance"
      tags = {
        service = "pulse"
      }
  }

  tag_specifications {
      resource_type = "volume"
      tags = {
        Name      = "pulse-otel-consumer-volume"
        service   = "pulse"
        ManagedBy = "terraform"
      }
  }

  user_data = base64encode(file("${path.module}/user-data.sh"))

  metadata_options {
         http_tokens                 = "required"
         http_put_response_hop_limit = 1
  }
}

# -------------------------------
# Auto Scaling Group for collectors
# -------------------------------
resource "aws_autoscaling_group" "otel-consumer" {
  name                      = "pulse-otel-consumer-asg"
  max_size                  = var.collector_count
  min_size                  = var.collector_count
  desired_capacity          = var.collector_count
  vpc_zone_identifier       = var.subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = 60

  launch_template {
    id      = aws_launch_template.otel-consumer.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-otel-consumer-instance"
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

