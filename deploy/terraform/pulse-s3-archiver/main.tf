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
    key          = "terraform/production/pulse-s3-archiver/terraform.tfstate"
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
resource "aws_launch_template" "pulse_s3_archiver" {
  name = "pulse-s3-archiver-lt"

  tags = {
    Name             = "pulse-s3-archiver-lt"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-s3-archiver"
    component_type   = "application"
    service_name     = "pulse"
    resource_type    = "lt"
  }

  image_id               = var.ami_id
  key_name               = var.ssh_key_name
  vpc_security_group_ids = var.ec2_security_group_ids

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name             = "pulse-s3-archiver-instance"
      org_name         = "horizon"
      environment_name = "production"
      component_name   = "pulse-s3-archiver"
      component_type   = "application"
      service_name     = "pulse"
      resource_type    = "ec2"
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name             = "pulse-s3-archiver-volume"
      org_name         = "horizon"
      environment_name = "production"
      component_name   = "pulse-s3-archiver"
      component_type   = "application"
      service_name     = "pulse"
      resource_type    = "ebs"
    }
  }

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
  }))
}

# -------------------------------------------------------------------
# Internal ALB + Target Group + Listener (healthcheck-driven rollout)
# -------------------------------------------------------------------
resource "aws_lb" "pulse_s3_archiver" {
  load_balancer_type         = "application"
  name                       = "pulse-s3-archiver-alb"
  internal                   = true
  ip_address_type            = "ipv4"
  subnets                    = var.alb_subnet_ids
  security_groups            = var.alb_security_group_ids
  enable_deletion_protection = false
  drop_invalid_header_fields = true

  tags = {
    Name             = "pulse-s3-archiver-lb"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-s3-archiver"
    component_type   = "application"
    service_name     = "pulse"
    resource_type    = "lb"
  }
}

resource "aws_lb_target_group" "pulse_s3_archiver" {
  target_type     = "instance"
  name            = "pulse-s3-archiver-tg"
  port            = var.healthcheck_port
  ip_address_type = "ipv4"
  vpc_id          = var.vpc_id
  protocol        = "HTTP"

  health_check {
    enabled             = true
    protocol            = "HTTP"
    path                = var.healthcheck_path
    port                = "traffic-port"
    healthy_threshold   = 5
    unhealthy_threshold = 2
    timeout             = 10
    interval            = 120
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name             = "pulse-s3-archiver-tg"
    org_name         = "horizon"
    environment_name = "production"
    component_name   = "pulse-s3-archiver"
    component_type   = "application"
    service_name     = "pulse"
    resource_type    = "tg"
  }
}

resource "aws_lb_listener" "pulse_s3_archiver_http" {
  load_balancer_arn = aws_lb.pulse_s3_archiver.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.pulse_s3_archiver.arn
  }
}

# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------
resource "aws_autoscaling_group" "pulse_s3_archiver" {
  name = "pulse-s3-archiver-asg"

  mixed_instances_policy {
    instances_distribution {
      on_demand_percentage_above_base_capacity = 0
      on_demand_base_capacity                  = var.asg_on_demand_base_capacity
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.pulse_s3_archiver.id
        version            = aws_launch_template.pulse_s3_archiver.latest_version
      }

      dynamic "override" {
        for_each = var.instance_types
        content {
          instance_type = override.value
        }
      }
    }
  }

  vpc_zone_identifier       = var.ec2_subnet_ids
  health_check_type         = "ELB"
  health_check_grace_period = 300
  desired_capacity          = var.desired_capacity
  min_size                  = var.asg_min_size
  max_size                  = var.asg_max_size
  protect_from_scale_in     = true
  target_group_arns = [aws_lb_target_group.pulse_s3_archiver.arn]

  instance_refresh {
    strategy = "Rolling"
    preferences {
      # desired_capacity=1 → ASG temporarily runs 2, waits for healthcheck on the new one,
      # then terminates the old. Avoids consumer-group downtime during deploys.
      min_healthy_percentage       = 100
      scale_in_protected_instances = "Refresh"
    }
    triggers = ["launch_template"]
  }

  tag {
    key                 = "Name"
    value               = "pulse-s3-archiver-asg"
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
    value               = "pulse-s3-archiver"
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
  }
}

# -------------------------------------------------------------------
# Route53 Alias Record (private zone — service discovery only)
# -------------------------------------------------------------------
resource "aws_route53_record" "pulse_s3_archiver" {
  zone_id = var.route53_local_zone_id
  name    = var.route53_record_name
  type    = "A"

  alias {
    name                   = aws_lb.pulse_s3_archiver.dns_name
    zone_id                = aws_lb.pulse_s3_archiver.zone_id
    evaluate_target_health = false
  }
}
