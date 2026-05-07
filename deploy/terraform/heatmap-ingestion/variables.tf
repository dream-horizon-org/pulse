variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID: should include node runtime + pm2 + unzip + aws cli (same AMI as session-ingestion is recommended)"
  type        = string
}

variable "artifact_version" {
  description = "CodeArtifact package version to deploy"
  type        = string
}

variable "instance_types" {
  description = "EC2 instance types for ASG mixed instances policy"
  type        = list(string)

  validation {
    condition     = length(var.instance_types) > 0
    error_message = "instance_types must contain at least one instance type."
  }
}

variable "desired_capacity" {
  description = "ASG desired capacity"
  type        = number
}

variable "asg_min_size" {
  description = "ASG minimum size"
  type        = number
}

variable "asg_max_size" {
  description = "ASG maximum size"
  type        = number
}

variable "asg_on_demand_base_capacity" {
  description = "Base on-demand instance count before spot"
  type        = number
}

variable "ssh_key_name" {
  description = "Existing SSH keypair name (optional)"
  type        = string
  default     = null
}

variable "instance_profile_name" {
  description = "IAM instance profile name attached to instances"
  type        = string
}

variable "ec2_subnet_ids" {
  description = "Private subnets for the ASG (use 2+ AZs for resilience)"
  type        = list(string)
}

variable "ec2_security_group_ids" {
  description = "Security group IDs attached to ingestion instances"
  type        = list(string)
}

variable "health_check_grace_period_seconds" {
  description = "ASG grace period after instance launch"
  type        = number
  default     = 120
}