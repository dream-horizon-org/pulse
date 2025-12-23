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
  name_prefix   = "pulse-otel-collector-1-"
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
      Role = "pulse-otel-collector-1"
      service = "pulse"
    }
  }

  tag_specifications {
      resource_type = "volume"

      tags = {
        Role        = "pulse-otel-collector-1"
        service     = "pulse"
        VolumeRole  = "root"
        ManagedBy   = "terraform"
      }
    }
}

# -------------------------------
# Target group for collectors (HTTP)
# -------------------------------
resource "aws_lb_target_group" "otel" {
  name        = "pulse-otel-collector-1-tg"
  port        = var.collector_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

   health_check {
      protocol = "HTTP"
      port     = var.health_check_port    # 13133, where health_check extension listens
      path     = var.health_check_path    # "/", for your config
      matcher  = "200-399"
   }
}

# -------------------------------
# Application Load Balancer (internal)
# -------------------------------
resource "aws_lb" "otel" {
  name               = "pulse-otel-collector-1-alb"
  internal           = true
  load_balancer_type = "application"
  security_groups    = var.alb_security_group_ids
  subnets            = var.subnet_ids
  drop_invalid_header_fields = true
}

resource "aws_lb_listener" "otel" {
  load_balancer_arn = aws_lb.otel.arn
  port              = var.collector_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.otel.arn
  }
}

# -------------------------------
# Auto Scaling Group for collectors
# -------------------------------
resource "aws_autoscaling_group" "otel" {
  name                      = "pulse-otel-collector-1-asg"
  max_size                  = var.collector_count
  min_size                  = var.collector_count
  desired_capacity          = var.collector_count
  vpc_zone_identifier       = var.subnet_ids
  health_check_type         = "ELB"
  health_check_grace_period = 60

  target_group_arns = [aws_lb_target_group.otel.arn]

  launch_template {
    id      = aws_launch_template.otel.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-otel-collector-1"
    propagate_at_launch = true
  }

  tag {
      key                 = "service"
      value               = "pulse"
      propagate_at_launch = true
    }
}

# -------------------------------
# Route53 record pointing to ALB
# -------------------------------
resource "aws_route53_record" "otel" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "CNAME"
  ttl     = 60

  records = [aws_lb.otel.dns_name]

  # optional but fine to keep if you're using default_tags
  # depends_on = [aws_lb.otel]
}

# -------------------------------
# Outputs
# -------------------------------
output "otel_alb_dns_name" {
  description = "dns name of the otel alb route53 record"
  value = aws_lb.otel.dns_name
}

output "otel_dns_name" {
  description = "dns name of the otel collector route53 record"
  value = aws_route53_record.otel.fqdn
}
