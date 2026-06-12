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

variable "replica_count" {
  description = "Number of replicas per shard"
  type        = number

  validation {
    condition     = var.replica_count >= 1 && var.replica_count <= length(var.subnets)
    error_message = "replica_count must be between 1 and the number of AZ subnets provided in var.subnets."
  }
}

variable "keeper_count" {
  description = "Number of ClickHouse keeper servers"
  type        = number

  validation {
    condition     = var.keeper_count <= length(var.subnets)
    error_message = "keeper_count must be <= number of subnets/AZs provided in var.subnets."
  }
}

variable "ami_id" {
  description = "AMI ID for pre-baked ClickHouse image"
  type        = string
}

variable "keeper_ami_id" {
  description = "AMI ID for pre-baked ClickHouse keeper image"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for ClickHouse servers"
  type        = string
}

variable "keeper_instance_type" {
  description = "EC2 instance type for ClickHouse keepers"
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

variable "clickhouse_user" {
    description = "username for clickhouse"
    type        = string
}


variable "clickhouse_password" {
    description = "password for clickhouse"
    type        = string
}

variable "data_volume_iops" {
  description = "IOPS for ClickHouse data volume"
  type        = number
  default     = 3000
}

variable "data_volume_throughput" {
  description = "Throughput (MB/s) for ClickHouse data volume"
  type        = number
  default     = 125
}