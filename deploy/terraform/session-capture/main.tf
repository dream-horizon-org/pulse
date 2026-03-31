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
    key          = "terraform/production/session-capture/terraform.tfstate"
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
  component_name = "pulse-session-capture"
  tag_base = {
    org_name         = "horizon"
    environment_name = "production"
    component_name   = local.component_name
    component_type   = "application"
    service_name     = "pulse"
  }
}

# -------------------------------------------------------------------
# Launch Template
# -------------------------------------------------------------------
resource "aws_launch_template" "capture" {
  name = "pulse-session-capture-lt"

  tags = merge(local.tag_base, {
    Name          = "pulse-session-capture-lt"
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
    artifact_version = var.artifact_version
    port             = var.listen_port
    kafka_brokers    = var.kafka_brokers
    kafka_topic      = var.kafka_topic
    rust_log         = var.rust_log
  }))

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.root_volume_size_gb
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"

    tags = merge(local.tag_base, {
      Name          = "pulse-session-capture-instance"
      resource_type = "ec2"
    })
  }

  tag_specifications {
    resource_type = "volume"

    tags = merge(local.tag_base, {
      Name          = "pulse-session-capture-volume"
      resource_type = "ebs"
    })
  }

  lifecycle {
    create_before_destroy = true
  }
}

# -------------------------------------------------------------------
# NLB + Target Group (session-capture)
# -------------------------------------------------------------------
resource "aws_lb_target_group" "capture" {
  name            = "pulse-session-capture-tg"
  port            = var.listen_port
  protocol        = "TCP"
  vpc_id          = var.vpc_id
  target_type     = "instance"
  ip_address_type = "ipv4"

  health_check {
    enabled             = true
    protocol            = "HTTP"
    port                = tostring(var.listen_port)
    path                = var.health_check_path
    healthy_threshold   = 5
    unhealthy_threshold = 2
    timeout             = 10
    interval            = 30
    matcher             = "200-399"
  }

  deregistration_delay = 30

  tags = merge(local.tag_base, {
    Name          = "pulse-session-capture-tg"
    resource_type = "tg"
  })
}

resource "aws_lb" "capture" {
  name                       = "pulse-session-capture-nlb"
  internal                   = true
  load_balancer_type         = "network"
  ip_address_type            = "ipv4"
  security_groups            = var.nlb_security_group_ids
  subnets                    = var.nlb_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = false

  tags = merge(local.tag_base, {
    Name          = "pulse-session-capture-nlb"
    resource_type = "lb"
  })
}

resource "aws_lb_listener" "capture" {
  load_balancer_arn = aws_lb.capture.arn
  port              = var.listen_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.capture.arn
  }
}

# -------------------------------------------------------------------
# Auto Scaling Group (mixed instances + instance refresh)
# -------------------------------------------------------------------
resource "aws_autoscaling_group" "capture" {
  name = "pulse-session-capture-asg"

  vpc_zone_identifier       = var.ec2_subnet_ids
  health_check_type         = "ELB"
  health_check_grace_period = var.health_check_grace_period_seconds
  desired_capacity          = var.desired_capacity
  min_size                  = var.asg_min_size
  max_size                  = var.asg_max_size
  protect_from_scale_in     = true
  target_group_arns         = [aws_lb_target_group.capture.arn]

  mixed_instances_policy {
    instances_distribution {
      on_demand_percentage_above_base_capacity = 0
      on_demand_base_capacity                  = var.asg_on_demand_base_capacity
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.capture.id
        version            = aws_launch_template.capture.latest_version
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
  }

  tag {
    key                 = "Name"
    value               = "pulse-session-capture-asg"
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

# -------------------------------------------------------------------
# Route53 alias to NLB
# -------------------------------------------------------------------
resource "aws_route53_record" "capture" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "A"

  alias {
    name                   = aws_lb.capture.dns_name
    zone_id                = aws_lb.capture.zone_id
    evaluate_target_health = true
  }
}
