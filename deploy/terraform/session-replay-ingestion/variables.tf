variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID: node-rdkafka runtime libs; app from CodeArtifact at boot; Node/pm2 installed by user-data if missing"
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
  description = "IAM instance profile: S3/Kafka as needed, CodeArtifact artifact pull, secretsmanager:GetSecretValue on prod/pulse-session-replay-ingestion/appenv"
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
  default     = 120
}

# Runtime env for the Node consumer is loaded at boot from AWS Secrets Manager
# secret prod/pulse-session-replay-ingestion/appenv — same JSON shape as pulse-server (app_env array).
# See backend/session-replay-ingestion/src/config.ts for supported keys (KAFKA_*, S3_*, MAX_BATCH_SIZE_KB, …).
