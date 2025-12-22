locals {
  vector_config = file("${path.module}/vector.yaml")
}

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
  region = var.region

  # Default tags applied to all resources
  default_tags {
    tags = {
      service = "pulse"
    }
  }
}

# -------------------------------------------------------------------
# Launch Template
# -------------------------------------------------------------------

resource "aws_launch_template" "vector" {
  name_prefix   = "pulse-vector-lt-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  key_name      = var.ssh_key_name

  iam_instance_profile {
    name = var.instance_profile_name
  }

  vpc_security_group_ids = [var.security_group_id]

  lifecycle {
    create_before_destroy = true
  }
  tag_specifications {
    resource_type = "instance"
    tags = {
      service = "pulse"
    }
  }
}

# -------------------------------------------------------------------
# ALB + Target Group + Listener
# -------------------------------------------------------------------

resource "aws_lb" "vector" {
  name               = "pulse-vector-alb"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.subnet_ids
}

resource "aws_lb_target_group" "vector" {
  name        = "pulse-vector-tg"
  port        = var.vector_listen_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    path                = var.healthcheck_path
    port                = tostring(var.healthcheck_port)
    matcher             = "200-399"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.vector.arn
  port              = var.vector_listen_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.vector.arn
  }
}

# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------

resource "aws_autoscaling_group" "vector" {
  name = "pulse-vector-asg"

  min_size         = var.instance_count
  max_size         = var.instance_count
  desired_capacity = var.instance_count

  vpc_zone_identifier = var.subnet_ids
  target_group_arns   = [aws_lb_target_group.vector.arn]

  health_check_type         = "EC2"
  health_check_grace_period = 60

  launch_template {
    id      = aws_launch_template.vector.id
    version = "$Latest"
  }

  # Explicit Name tag to override AWS defaults
  tag {
    key                 = "Name"
    value               = "pulse-vector-instance"
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
# Route53 Alias Record
# -------------------------------------------------------------------

resource "aws_route53_record" "vector" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "CNAME"
  records = [aws_lb.vector.dns_name]
  ttl = 60
}
