# -------------------------------------------------------------------
# AWS
# -------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID for Kafka EC2 instances"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for Kafka brokers"
  type        = string
  default     = "t3.medium"
}

variable "num_brokers" {
  description = "Number of Kafka broker instances (also used as controller count in combined mode)"
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
  description = "Route53 private hosted zone name (e.g. pulse.internal) — used to build broker FQDNs"
  type        = string
}

# -------------------------------------------------------------------
# EBS
# -------------------------------------------------------------------
variable "ebs_size_gb" {
  description = "EBS data volume size per broker in GB (separate from root OS disk)"
  type        = number
  default     = 20  
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
  default     = 125
}

# -------------------------------------------------------------------
# Kafka
# -------------------------------------------------------------------
variable "kafka_version" {
  description = "Apache Kafka version to install (must match Scala 2.13 build)"
  type        = string
  default     = "4.1.2"
}

variable "kafka_data_dir" {
  description = "Mount point for the dedicated EBS data volume"
  type        = string
  default     = "/var/lib/kafka"
}

variable "replication_factor" {
  description = "Default replication factor for topics (max = num_brokers)"
  type        = number
  default     = 2
}

variable "min_insync_replicas" {
  description = "Minimum in-sync replicas required for a produce to succeed"
  type        = number
  default     = 1
}

variable "retention_hours" {
  description = "Message retention period in hours"
  type        = number
  default     = 1
}

variable "compression_type" {
  description = "Broker-side compression codec (gzip | snappy | lz4 | zstd | none)"
  type        = string
  default     = "gzip"
}

# -------------------------------------------------------------------
# Topics
# -------------------------------------------------------------------
variable "kafka_topics" {
  description = "Topics to create on first boot of node-01. Idempotent — safe to re-apply."
  type = list(object({
    name       = string
    partitions = number
  }))
  default = []
}
