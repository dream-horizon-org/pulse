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

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to OTEL instances"
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

