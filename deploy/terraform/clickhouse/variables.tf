variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "cluster_name" {
  description = "Logical name of the ClickHouse cluster"
  type        = string
}

variable "shard_count" {
  description = "Number of ClickHouse shards (servers)"
  type        = number
}

variable "ami_id" {
  description = "AMI ID for pre-baked ClickHouse image"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for ClickHouse servers"
  type        = string
}

variable "key_name" {
  description = "SSH key pair name to attach to instances"
  type        = string
}

variable "subnets" {
  description = "Map of AZ name -> subnet ID (e.g., ap-south-1a = subnet-xxxx)"
  type        = map(string)
}

variable "availability_zone" {
  description = "AZ whose subnet should be used for ClickHouse (must be a key in var.subnets)"
  type        = string

  validation {
    condition     = contains(keys(var.subnets), var.availability_zone)
    error_message = "availability_zone must be one of the keys in var.subnets."
  }
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to ClickHouse instances"
  type        = list(string)
}

variable "iam_instance_profile" {
  description = "IAM instance profile name to attach to EC2 instances"
  type        = string
}

variable "data_volume_size" {
  description = "Size of secondary data volume (GB)"
  type        = number
  default     = 200
}

variable "data_volume_type" {
  description = "EBS volume type for data volume"
  type        = string
  default     = "gp3"
}

variable "private_zone_id" {
  description = "Route53 private hosted zone ID for internal DNS"
  type        = string
}

variable "private_zone_domain" {
  description = "Domain name of the private hosted zone, e.g. internal.example.com"
  type        = string
}
