variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID: node-rdkafka runtime libs + systemd unit; app dist/node_modules from CodeArtifact at boot"
  type        = string
}

variable "artifact_version" {
  description = "CodeArtifact package version to deploy (same pattern as pulse-server TF_VAR_artifact_version)"
  type        = string
}

variable "instance_types" {
  description = "EC2 instance types for ASG mixed instances policy (pulse-server pattern)"
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
  description = "IAM instance profile: S3/Kafka as needed, plus codeartifact:GetPackageVersionAsset and sts:GetServiceBearerToken for boot-time artifact pull"
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
  default     = 300
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
