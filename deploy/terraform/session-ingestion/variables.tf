variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "Golden AMI: Node.js runtime + built dist + systemd unit pulse-session-replay-ingestion (no git/npm on boot)"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "c6i.xlarge"
}

variable "ingestion_count" {
  description = "Number of consumer instances (same Kafka group; scale with partition count)"
  type        = number
}

variable "ssh_key_name" {
  description = "Existing SSH keypair name (optional)"
  type        = string
  default     = null
}

variable "instance_profile_name" {
  description = "IAM instance profile (S3 write to recordings bucket; use instance role, omit static keys in env)"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnets for the ASG (use 2+ AZs for resilience)"
  type        = list(string)
}

variable "vpc_security_group_ids" {
  description = "Security group IDs attached to ingestion instances"
  type        = list(string)
}

variable "health_check_grace_period_seconds" {
  description = "ASG grace period after instance launch"
  type        = number
  default     = 120
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size (GiB)"
  type        = number
  default     = 30
}

# --- App config (written to /etc/pulse/ingestion.env at boot) ---

variable "kafka_brokers" {
  description = "Kafka broker address(es), e.g. broker-01.kafka.internal:9092"
  type        = string
}

variable "kafka_topic" {
  description = "Kafka topic for raw session recording events"
  type        = string
  default     = "session_recording_events"
}

variable "kafka_metadata_topic" {
  description = "Kafka topic for session block metadata (ClickHouse pipeline)"
  type        = string
  default     = "clickhouse_session_replay_events"
}

variable "kafka_group_id" {
  description = "Kafka consumer group ID (shared by all ASG instances)"
  type        = string
  default     = "session-replay-ingestion"
}

variable "s3_endpoint" {
  description = "S3 API endpoint (AWS: https://s3.<region>.amazonaws.com)"
  type        = string
}

variable "s3_bucket" {
  description = "S3 bucket for session recordings"
  type        = string
}

variable "s3_region" {
  description = "S3 bucket region"
  type        = string
  default     = "ap-south-1"
}

variable "s3_prefix" {
  description = "S3 key prefix for session recordings"
  type        = string
  default     = "session-recordings"
}

variable "max_batch_size_kb" {
  description = "Max batch size in KB before flush"
  type        = string
  default     = "10240"
}

variable "max_batch_age_ms" {
  description = "Max batch age in ms before flush"
  type        = string
  default     = "10000"
}

variable "fetch_batch_size" {
  description = "Kafka messages to fetch per consume() call"
  type        = string
  default     = "500"
}

variable "s3_timeout_ms" {
  description = "S3 client timeout (ms)"
  type        = string
  default     = "30000"
}
