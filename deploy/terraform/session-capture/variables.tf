variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "ami_id" {
  description = "AMI ID: systemd unit pulse-session-capture + OS/libs; app binary fetched from CodeArtifact at boot (see user-data.sh)"
  type        = string
}

variable "artifact_version" {
  description = "CodeArtifact package version to deploy (same pattern as pulse-server TF_VAR_artifact_version)"
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
  description = "IAM instance profile: allow codeartifact:GetPackageVersionAsset + sts:GetServiceBearerToken for boot artifact pull (and any VPC endpoints as for pulse-server)"
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

# App runtime config is loaded from AWS Secrets Manager secret prod/pulse-session-capture/appenv
# Terraform listen_port must match PORT in that secret for NLB/TG/health checks.
