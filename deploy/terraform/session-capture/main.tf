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

# -------------------------------
# Launch template for session capture
# -------------------------------
resource "aws_launch_template" "capture" {
  name_prefix   = "pulse-session-capture-"
  image_id      = var.ami_id
  instance_type = var.instance_type

  key_name = var.ssh_key_name

  vpc_security_group_ids = var.vpc_security_group_ids

  instance_market_options {
    market_type = "spot"
  }

  iam_instance_profile {
    name = var.instance_profile_name
  }

  user_data = base64encode(templatefile("${path.module}/user-data.sh", {
    port          = var.listen_port
    kafka_brokers = var.kafka_brokers
    kafka_topic   = var.kafka_topic
    rust_log      = var.rust_log
  }))

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.root_volume_size_gb
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
      Role    = "pulse-session-capture"
      service = "pulse"
    }
  }

  tag_specifications {
    resource_type = "volume"

    tags = {
      Role       = "pulse-session-capture"
      service    = "pulse"
      VolumeRole = "root"
      ManagedBy  = "terraform"
    }
  }

  metadata_options {
    http_tokens = "required"
  }
}

# -------------------------------
# Target group (TCP → HTTP capture service)
# -------------------------------
resource "aws_lb_target_group" "capture" {
  name        = "pulse-session-capture-tg"
  port        = var.listen_port
  protocol    = "TCP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    protocol = "HTTP"
    port     = tostring(var.listen_port)
    path     = var.health_check_path
    matcher  = "200-399"
  }
}

# -------------------------------
# Network Load Balancer (internal)
# -------------------------------
resource "aws_lb" "capture" {
  name                       = "pulse-session-capture-nlb"
  internal                   = true
  load_balancer_type         = "network"
  security_groups            = var.nlb_security_group_ids
  subnets                    = var.private_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = false
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

# -------------------------------
# Auto Scaling Group
# -------------------------------
resource "aws_autoscaling_group" "capture" {
  name                      = "pulse-session-capture-asg"
  max_size                  = var.capture_count
  min_size                  = var.capture_count
  desired_capacity          = var.capture_count
  vpc_zone_identifier       = var.private_subnet_ids
  health_check_type         = "ELB"
  health_check_grace_period = var.health_check_grace_period_seconds

  target_group_arns = [aws_lb_target_group.capture.arn]

  launch_template {
    id      = aws_launch_template.capture.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "pulse-session-capture"
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

# -------------------------------
# Route53 record pointing to NLB
# -------------------------------
resource "aws_route53_record" "capture" {
  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "CNAME"
  ttl     = 60

  records = [aws_lb.capture.dns_name]
}
