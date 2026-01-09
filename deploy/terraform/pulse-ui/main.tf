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

# -------------------------------------------------------------------
# Launch Template
# -------------------------------------------------------------------

resource "aws_launch_template" "pulse_ui" {
  name_prefix   = "pulse-ui-lt-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  key_name      = var.ssh_key_name

  iam_instance_profile {
    name = var.instance_profile_name
  }

  vpc_security_group_ids = [var.security_group_id]

  instance_market_options {
    market_type = "spot"
  }

  metadata_options {
    http_tokens = "required"
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
      Name      = "pulse-ui-volume"
      service   = "pulse"
      ManagedBy = "terraform"
    }
  }

  lifecycle {
    create_before_destroy = true
  }

  user_data = base64encode(templatefile("${path.module}/user-data.sh", {
    env_vars = var.app_env
  }))
}

# -------------------------------------------------------------------
# NLB + Target Group + Listener
# -------------------------------------------------------------------

resource "aws_lb" "pulse_ui" {
  name                       = "pulse-ui-alb"
  internal                   = true
  load_balancer_type         = "application"
  subnets                    = var.subnet_ids
  security_groups            = var.vpc_security_group_ids
  enable_deletion_protection = false
}

resource "aws_lb_target_group" "pulse_ui" {
  name     = "pulse-ui-tg"
  port     = var.healthcheck_port
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = var.healthcheck_path
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name    = "pulse-ui-target-group"
    service = "pulse"
  }
}

resource "aws_lb_listener" "pulse_ui" {
  load_balancer_arn = aws_lb.pulse_ui.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.pulse_ui.arn
  }
}


# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------

resource "aws_autoscaling_group" "pulse_ui" {
  name = "pulse-ui-asg"

  min_size         = var.instance_count
  max_size         = var.instance_count
  desired_capacity = var.instance_count

  vpc_zone_identifier = var.subnet_ids
  target_group_arns   = [aws_lb_target_group.pulse_ui.arn]

  health_check_type         = "EC2"
  health_check_grace_period = 60

  launch_template {
    id      = aws_launch_template.pulse_ui.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-ui-instance"
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
# -------------------------------------------------------------------
# Route53 Alias Record (recommended)
# -------------------------------------------------------------------

resource "aws_route53_record" "pulse_ui" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "A"

  alias {
    name                   = aws_lb.pulse_ui.dns_name
    zone_id                = aws_lb.pulse_ui.zone_id
    evaluate_target_health = false
  }
}

