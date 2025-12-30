variable "region" {
  description = "AWS region"
  type        = string
}

variable "ami_id" {
  description = "AMI ID for the Vector.dev instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
}

variable "instance_count" {
  description = "Number of Vector instances"
  type        = number
}

variable "subnet_ids" {
  description = "List of subnet IDs for ALB and instances"
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group to attach to all resources"
  type        = string
}

variable "alb_security_group_id" {
  description = "alb Security group to attach to all resources"
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
  description = "Healthcheck path for ALB TG"
  type        = string
}

variable "healthcheck_port" {
  description = "Healthcheck port"
  type        = number
}

variable "vector_listen_port" {
  description = "vector port"
  type        = number
}
