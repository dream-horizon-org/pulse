# -------------------------------------------------------------------
# AWS
# -------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID for Kafka EC2 instances (pre-baked with Kafka installed)"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for Kafka brokers"
  type        = string
  default     = "t3.medium"
}

variable "num_brokers" {
  description = "Number of Kafka broker instances"
  type        = number
  default     = 2
}

# -------------------------------------------------------------------
# Networking
# -------------------------------------------------------------------
variable "private_subnet_ids" {
  description = "List of private subnet IDs for Kafka instances"
  type        = list(string)
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to Kafka instances"
  type        = list(string)
}

# -------------------------------------------------------------------
# Access
# -------------------------------------------------------------------
variable "key_name" {
  description = "EC2 SSH key pair name"
  type        = string
}

variable "iam_instance_profile" {
  description = "IAM instance profile name for Kafka EC2 instances"
  type        = string
}

# -------------------------------------------------------------------
# DNS
# -------------------------------------------------------------------
variable "route53_zone_id" {
  description = "Route53 private hosted zone ID"
  type        = string
}

variable "route53_zone_name" {
  description = "Route53 private hosted zone name (e.g. pulse.local) — used to build broker FQDNs"
  type        = string
}

# -------------------------------------------------------------------
# EBS
# -------------------------------------------------------------------
variable "ebs_size_gb" {
  description = "EBS data volume size per broker in GB"
  type        = number
  default     = 250
}

variable "ebs_type" {
  description = "EBS volume type"
  type        = string
  default     = "gp3"
}

variable "ebs_encrypted" {
  description = "Encrypt EBS volumes"
  type        = bool
  default     = true
}

variable "ebs_iops" {
  description = "Provisioned IOPS for gp3 volumes (min 3000)"
  type        = number
  default     = 3000
}

variable "ebs_throughput" {
  description = "Throughput in MB/s for gp3 volumes (125–1000)"
  type        = number
  default     = 250
}
