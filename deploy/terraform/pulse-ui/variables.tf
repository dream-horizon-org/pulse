# -----------------------------
# Core infra vars
# -----------------------------
variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "ami_id" {
  description = "AMI ID for the backend instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
}

variable "instance_count" {
  description = "Number of instances"
  type        = number
}

variable "subnet_ids" {
  description = "Subnets for instances (ASG)"
  type        = list(string)
}

variable "vpc_security_group_ids" {
  description = "Public subnets for the ALB"
  type        = list(string)
}

variable "security_group_id" {
  description = "Instance security group"
  type        = string
}

variable "ssh_key_name" {
  description = "Existing SSH keypair name"
  type        = string
}

variable "instance_profile_name" {
  description = "IAM instance profile name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID"
  type        = string
}

variable "route53_record_name" {
  description = "DNS record name"
  type        = string
}

variable "healthcheck_path" {
  description = "Healthcheck path"
  type        = string
}

variable "healthcheck_port" {
  description = "Healthcheck port"
  type        = number
}

# -----------------------------
# App deployment vars
# -----------------------------

variable "app_env" {
  description = "Environment variables for the app"
  type = list(object({
    key   = string
    value = string
  }))
  default = []
}

