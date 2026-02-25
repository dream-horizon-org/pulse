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
    key          = "terraform/production/terraform.tfstate"
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
resource "aws_launch_template" "pulse_ui" {
  name = "pulse-ui-test-lt"

  tags = {
    Name                = "pulse-ui-test-lt"
    org_name            = "horizon"
    environment_name    = "production"
    provisioned-by-user = "yasir.ansari@dream11.com"
    component_name      = "pulse-ui"
    component_type      = "application"
    service_name        = "pulse"
    resource_type       = "lt"
  }

  image_id               = var.ami_id
  key_name               = var.ssh_key_name
  vpc_security_group_ids = var.ec2_security_group_ids

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name                = "pulse-ui-test-instance"
      org_name            = "horizon"
      environment_name    = "production"
      provisioned-by-user = "yasir.ansari@dream11.com"
      component_name      = "pulse-ui"
      component_type      = "application"
      service_name        = "pulse"
      resource_type       = "ec2"
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name                = "pulse-ui-test-volume"
      org_name            = "horizon"
      environment_name    = "production"
      provisioned-by-user = "yasir.ansari@dream11.com"
      component_name      = "pulse-ui"
      component_type      = "application"
      service_name        = "pulse"
      resource_type       = "ebs"
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
# NLB + Target Group + Listener
# -------------------------------------------------------------------
resource "aws_lb" "pulse_ui" {
  load_balancer_type         = "application"
  name                       = "pulse-ui-test-alb"
  internal                   = true
  ip_address_type            = "ipv4"
  subnets                    = var.alb_subnet_ids
  security_groups            = var.alb_security_group_ids
  enable_deletion_protection = false
  drop_invalid_header_fields = true

  tags = {
    Name                = "pulse-ui-test-lb"
    org_name            = "horizon"
    environment_name    = "production"
    provisioned-by-user = "yasir.ansari@dream11.com"
    component_name      = "pulse-ui"
    component_type      = "application"
    service_name        = "pulse"
    resource_type       = "lb"
  }
}

resource "aws_lb_target_group" "pulse_ui" {
  target_type     = "instance"
  name            = "pulse-ui-test-tg"
  port            = 3000
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
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name                = "pulse-ui-test-tg"
    org_name            = "horizon"
    environment_name    = "production"
    provisioned-by-user = "yasir.ansari@dream11.com"
    component_name      = "pulse-ui"
    component_type      = "application"
    service_name        = "pulse"
    resource_type       = "tg"
  }
}

resource "aws_lb_listener" "pulse_ui_https" {
  load_balancer_arn = aws_lb.pulse_ui.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = var.acm_cert

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.pulse_ui.arn
  }
}

resource "aws_lb_listener" "pulse_ui_http" {
  load_balancer_arn = aws_lb.pulse_ui.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------
resource "aws_autoscaling_group" "pulse_ui" {
  name = "pulse-ui-test-asg"

  mixed_instances_policy {
    instances_distribution {
      on_demand_percentage_above_base_capacity = 0
      on_demand_base_capacity                  = 0
      spot_allocation_strategy                 = "price-capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.pulse_ui.id
        version            = aws_launch_template.pulse_ui.latest_version
      }
      # The correct way to pass a list of specific instance types
      dynamic "override" {
        for_each = var.instance_types
        content {
          instance_type = override.value
        }
      }
    }
  }

  vpc_zone_identifier       = var.ec2_subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = 300
  desired_capacity          = var.desired_capacity
  min_size                  = var.asg_min_size
  max_size                  = var.asg_max_size
  protect_from_scale_in     = true
  target_group_arns = [aws_lb_target_group.pulse_ui.arn]

  instance_refresh {
    strategy = "Rolling"
    preferences {
      # This ensures your app doesn't go down during the refresh.
      # Since your desired capacity is 1, AWS will temporarily spin up a 2nd instance,
      # wait for it to pass ALB health checks, and THEN terminate the old 1st instance.
      min_healthy_percentage = 100
      scale_in_protected_instances = "Refresh"
    }
    # This tells Terraform to trigger a refresh if the Launch Template changes
    triggers = ["launch_template"]
  }

  tag {
    key                 = "Name"
    value               = "pulse-ui-test-asg"
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
    key                 = "provisioned-by-user"
    value               = "yasir.ansari@dream11.com"
    propagate_at_launch = false
  }
  tag {
    key                 = "component_name"
    value               = "pulse-ui"
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
# Route53 Alias Record (recommended)
# -------------------------------------------------------------------
resource "aws_route53_record" "pulse_ui" {
  zone_id = var.route53_zone_id
  name    = "fancode1"
  type    = "A"

  alias {
    name                   = aws_lb.pulse_ui.dns_name
    zone_id                = aws_lb.pulse_ui.zone_id
    evaluate_target_health = false
  }
}