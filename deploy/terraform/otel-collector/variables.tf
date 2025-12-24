variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID for pre-baked OTEL collector image"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for OTEL collectors"
  type        = string
  default     = "t3.medium"
}

variable "collector_count" {
  description = "Number of OTEL collector instances (ASG desired/min/max)"
  type        = number
}

variable "public_subnet_ids" {
  description = "List of public subnets for the NLB"
  type        = list(string)
}

variable "acm_certificate_arn" {
  description = "acm certificate to add to the listener"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of public subnets for the collector"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID where collectors/NLB live"
  type        = string
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to OTEL instances"
  type        = list(string)
}

variable "nlb_security_group_ids" {
  description = "Security group IDs to attach to the OTEL ALB"
  type        = list(string)
}

variable "key_name" {
  description = "EC2 SSH key pair name (optional)"
  type        = string
  default     = null
}

variable "iam_instance_profile" {
  description = "IAM instance profile name to attach to OTEL EC2 instances (optional)"
  type        = string
  default     = null
}

variable "collector_port" {
  description = "Port OTEL collector listens on HTTP (e.g., OTLP/HTTP = 4318)"
  type        = number
  default     = 4318
}

variable "health_check_port" {
  description = "Port used by OTEL health_check extension"
  type        = number
  default     = 13133
}

variable "health_check_path" {
  description = "HTTP path on OTEL health_check endpoint"
  type        = string
  default     = "/"
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID for OTEL DNS record"
  type        = string
}

variable "route53_record_name" {
  description = "DNS name for OTEL collectors (e.g., otel.delivr.local)"
  type        = string
}
