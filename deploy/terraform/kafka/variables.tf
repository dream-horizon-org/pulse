variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "instance_type" {
  type    = string
  default = "t3.large"
}

variable "kafka_version" {
  type    = string
  default = "3.6.1"
}

variable "key_name" {
  description = "EC2 SSH key pair name (optional)"
  type        = string
  default     = null
}

variable "ami_id" {
  description = "Debian AMI ID to use for controllers and brokers"
  type        = string
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to Kafka instances"
  type        = list(string)
}

variable "iam_instance_profile" {
  description = "IAM instance profile name to attach to Kafka EC2 instances (optional)"
  type        = string
  default     = null
}

variable "private_subnet_ids" {
  description = "List of private subnets for the kafka instances"
  type        = list(string)
}

variable "num_brokers" {
  description = "Number of brokers"
  type        = number
  default     = 3
}

variable "num_controllers" {
  description = "Number of controllers (odd recommended: 3/5)"
  type        = number
  default     = 3
}

variable "route53_zone_name" {
  description = "Existing Route53 PRIVATE hosted zone name (e.g. kafka.internal)"
  type        = string
}

variable "route53_zone_id" {
  description = "Route53 hosted zone id for Kafka DNS record"
  type        = string
}

# ---- Kafka data + EBS ----

variable "kafka_data_dir" {
  description = "Mount point for Kafka data volume (log.dirs)"
  type        = string
  default     = "/var/lib/kafka"
}

variable "kafka_ebs_size_gb" {
  description = "EBS volume size (GB) for Kafka data disk"
  type        = number
  default     = 200
}

variable "kafka_ebs_type" {
  description = "EBS volume type for Kafka data disk"
  type        = string
  default     = "gp3"
}

variable "kafka_ebs_encrypted" {
  description = "Encrypt Kafka EBS volumes"
  type        = bool
  default     = true
}

variable "kafka_ebs_iops" {
  description = "gp3 only: provisioned IOPS (min 3000). Set null to use AWS default."
  type        = number
  default     = 3000
}

variable "kafka_ebs_throughput" {
  description = "gp3 only: throughput in MB/s (125-1000). Set null to use AWS default."
  type        = number
  default     = 125
}