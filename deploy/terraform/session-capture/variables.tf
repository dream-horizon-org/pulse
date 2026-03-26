variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID with pulse-session-capture binary + systemd unit pre-baked"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "c6i.xlarge"
}

variable "capture_count" {
  description = "Number of session-capture instances (ASG desired/min/max)"
  type        = number
}

variable "ssh_key_name" {
  description = "Existing SSH keypair name (optional)"
  type        = string
  default     = null
}

variable "instance_profile_name" {
  description = "IAM instance profile name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where NLB and target group live"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnets for ASG instances and NLB"
  type        = list(string)
}

variable "vpc_security_group_ids" {
  description = "Security group IDs to attach to session-capture instances"
  type        = list(string)
}

variable "nlb_security_group_ids" {
  description = "Security group IDs to attach to the internal NLB"
  type        = list(string)
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID for the capture service DNS record"
  type        = string
}

variable "route53_record_name" {
  description = "DNS name for session-capture (e.g. capture.internal.example.com)"
  type        = string
}

variable "listen_port" {
  description = "TCP/HTTP port the capture service listens on (NLB listener + target group + health check)"
  type        = number
  default     = 3400
}

variable "health_check_path" {
  description = "HTTP path for NLB target group health checks"
  type        = string
  default     = "/healthcheck"
}

variable "health_check_grace_period_seconds" {
  description = "ASG grace period before ELB health checks affect instance replacement"
  type        = number
  default     = 60
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size (GiB) for capture instances"
  type        = number
  default     = 20
}

# --- App config (user-data) ---

variable "kafka_brokers" {
  description = "Kafka broker address(es), e.g. broker-01.kafka.internal:9092"
  type        = string
}

variable "kafka_topic" {
  description = "Kafka topic for session recording events"
  type        = string
  default     = "session_recording_events"
}

variable "rust_log" {
  description = "Rust log level"
  type        = string
  default     = "pulse_session_capture=info"
}
