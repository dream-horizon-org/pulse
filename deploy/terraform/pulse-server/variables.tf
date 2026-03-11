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

variable "instance_types" {
  description = "List of EC2 instance types for the ASG mixed instances policy"
  type        = list(string)
}

variable "desired_capacity" {
  description = "Number of desired instances"
  type        = number
}

variable "asg_min_size" {
  description = "Number of min instances"
  type        = number
}

variable "asg_max_size" {
  description = "Number of max instances"
  type        = number
}

variable "asg_on_demand_base_capacity" {
  description = "Number of base on demand instances"
  type        = number
}

variable "ec2_subnet_ids" {
  description = "Subnets for instances (ASG)"
  type        = list(string)
}

variable "alb_subnet_ids" {
  description = "Subnets for ALB"
  type        = list(string)
}

variable "alb_security_group_ids" {
  description = "SG for the ALB"
  type        = list(string)
}

variable "ec2_security_group_ids" {
  description = "EC2 Security Group IDs"
  type        = list(string)
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

variable "route53_com_zone_id" {
  description = "Route53 hosted zone ID for pulse-ux.com"
  type        = string
}

variable "route53_local_zone_id" {
  description = "Route53 hosted zone ID for pulse.local"
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

variable "artifact_version" {
  description = "Artifact version"
  type        = string
}

variable "cloudfront_distribution" {
  description = "Cloudfront distribution id for pulse-server"
  type = string
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

variable "acm_cert" {
    description = "acm of the ssl certificate"
    type        = string
}

variable "ssl_policy" {
    description = "ssl policy to be used"
    type        = string
}

