variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID with pulse-session-capture binary + systemd unit pre-baked"
  type        = string
}

variable "instance_types" {
  description = "EC2 instance types for ASG mixed instances policy (same pattern as pulse-server)"
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
  description = "Base on-demand instance count before spot (pulse-server pattern)"
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

variable "ec2_subnet_ids" {
  description = "Private subnets for session-capture ASG instances"
  type        = list(string)
}

variable "nlb_subnet_ids" {
  description = "Subnets for the internal NLB (often same as ec2_subnet_ids; must span AZs as required by NLB)"
  type        = list(string)
}

variable "ec2_security_group_ids" {
  description = "Security group IDs attached to session-capture instances (pulse-server naming)"
  type        = list(string)
}

variable "nlb_security_group_ids" {
  description = "Security group IDs attached to the internal NLB"
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
  default     = 300
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
