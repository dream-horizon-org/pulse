variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID for pre-baked OTEL collector image"
  type        = string
}

variable "instance_types" {
  description = "List of EC2 instance types for the ASG mixed instances policy"
  type        = list(string)
}

variable "asg_on_demand_base_capacity" {
  description = "Number of on-demand instances to maintain as base capacity in ASG"
  type        = number
  default     = 0
}

variable "collector_count" {
  description = "Number of OTEL collector instances (ASG desired/min/max)"
  type        = number
}

variable "subnet_ids" {
  description = "List of subnets (usually private) for the collectors/ALB"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID for OTEL consumer target group"
  type        = string
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to OTEL instances"
  type        = list(string)
}

variable "alb_security_group_ids" {
  description = "Security group IDs to attach to the OTEL consumer ALB"
  type        = list(string)
}


variable "key_name" {
  description = "EC2 SSH key pair name (optional)"
  type        = string
  default     = null
}

variable "instance_profile_name" {
  description = "IAM instance profile name to attach to OTEL EC2 instances (optional)"
  type        = string
  default     = null
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID for OTEL consumer ALB alias record"
  type        = string
}

variable "route53_record_name" {
  description = "DNS record name for OTEL consumer ALB (for example, pulse-otel-consumer.pulse.local)"
  type        = string
}

variable "route53_zone_name" {
  description = "Private zone DNS name without trailing dot (kept for backward compatibility)"
  type        = string
  default     = "pulse.local"
}

variable "alb_listener_port" {
  description = "ALB listener port for OTEL consumer traffic"
  type        = number
  default     = 4318
}

variable "alb_target_group_port" {
  description = "Target group port on OTEL consumer instances"
  type        = number
  default     = 4318
}

variable "healthcheck_path" {
  description = "Health check path for OTEL consumer target group"
  type        = string
  default     = "/"
}

variable "healthcheck_port" {
  description = "Health check port for OTEL consumer target group"
  type        = number
  default     = 13133
}

