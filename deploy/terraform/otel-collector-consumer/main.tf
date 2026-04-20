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

# -------------------------------------------------------------------
# Launch Template
# -------------------------------------------------------------------

resource "aws_launch_template" "otel-consumer" {
  name     = "pulse-otel-consumer-lt"
  image_id = var.ami_id
  key_name = var.key_name

  tags = {
    Name             = "pulse-otel-consumer-lt"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-otel-consumer"
    component_type   = "application"
    service_name     = "pulse"
    resource_type    = "lt"
  }

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
      Name             = "pulse-otel-consumer-instance"
      org_name         = "horizon"
      environment_name = "production"
      component_name   = "pulse-otel-consumer"
      component_type   = "application"
      service_name     = "pulse"
      resource_type    = "ec2"
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name             = "pulse-otel-consumer-volume"
      org_name         = "horizon"
      environment_name = "production"
      component_name   = "pulse-otel-consumer"
      component_type   = "application"
      service_name     = "pulse"
      resource_type    = "ebs"
    }
  }

  user_data = base64encode(file("${path.module}/user-data.sh"))

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }
}

# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------

resource "aws_autoscaling_group" "otel-consumer" {
  name                      = "pulse-otel-consumer-asg"
  max_size                  = var.collector_count
  min_size                  = var.collector_count
  desired_capacity          = var.collector_count
  vpc_zone_identifier       = var.subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = 60

  mixed_instances_policy {
    instances_distribution {
      on_demand_percentage_above_base_capacity = 0
      on_demand_base_capacity                  = var.asg_on_demand_base_capacity
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.otel-consumer.id
        version            = aws_launch_template.otel-consumer.latest_version
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
    preferences {
      min_healthy_percentage       = 100
      scale_in_protected_instances = "Refresh"
    }
    triggers = ["launch_template"]
  }

  tag {
    key                 = "Name"
    value               = "pulse-otel-consumer-asg"
    propagate_at_launch = false
  }
  tag {
    key                 = "org_name"
    value               = "horizon"
    propagate_at_launch = false
  }
  tag {
    key                 = "environment_name"
    value               = "production"
    propagate_at_launch = false
  }
  tag {
    key                 = "component_name"
    value               = "pulse-otel-consumer"
    propagate_at_launch = false
  }
  tag {
    key                 = "component_type"
    value               = "application"
    propagate_at_launch = false
  }
  tag {
    key                 = "service_name"
    value               = "pulse"
    propagate_at_launch = false
  }
  tag {
    key                 = "resource_type"
    value               = "ec2"
    propagate_at_launch = false
  }

  lifecycle {
    create_before_destroy = true
    ignore_changes        = [launch_template]
  }
}