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
      key          = "terraform/production/pulse-otel-producer/terraform.tfstate"
      region       = "ap-south-1"
      use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

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

resource "aws_launch_template" "otel-producer" {
  name_prefix   = "pulse-otel-producer-lt-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  key_name      = var.ssh_key_name

  iam_instance_profile {
    name = var.instance_profile_name
  }

  vpc_security_group_ids = var.security_group_ids

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
      Name      = "pulse-otel-producer-volume"
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

# -------------------------------------------------------------------
# NLB + Target Group + Listener
# -------------------------------------------------------------------

resource "aws_lb" "otel-producer" {
  name               = "pulse-otel-producer-nlb"
  internal           = true
  load_balancer_type = "network"
  security_groups    = var.nlb_security_group_ids
  subnets            = var.private_nlb_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = false
}


resource "aws_lb_target_group" "otel-producer" {
  name        = "pulse-otel-producer-tg"
  port        = var.otel_listen_port
  protocol    = "TCP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    path                = var.healthcheck_path
    port                = tostring(var.healthcheck_port)
    matcher             = "200-399"
    protocol            = "HTTP"
  }
}


resource "aws_lb_listener" "otel-producer" {
  load_balancer_arn = aws_lb.otel-producer.arn
  port              = var.otel_listen_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.otel-producer.arn
  }
}

# -------------------------------------------------------------------
# Autoscaling Group
# -------------------------------------------------------------------

resource "aws_autoscaling_group" "aws_autoscaling_group" {
  name = "pulse-otel-producer-asg"

  min_size         = var.instance_count
  max_size         = var.instance_count
  desired_capacity = var.instance_count

  vpc_zone_identifier = var.private_ec2_subnet_ids
  target_group_arns   = [aws_lb_target_group.otel-producer.arn]

  health_check_type         = "EC2"
  health_check_grace_period = 60

  launch_template {
    id      = aws_launch_template.otel-producer.id
    version = "$Latest"
  }

  # Explicit Name tag to override AWS defaults
  tag {
    key                 = "Name"
    value               = "pulse-otel-producer-instance"
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

resource "aws_route53_record" "otel-producer" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "CNAME"
  records = [aws_lb.otel-producer.dns_name]
  ttl = 60
}
