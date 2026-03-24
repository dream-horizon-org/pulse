variable "aws_region" {
  description = "AWS region (Pulse default: ap-south-1)"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment name for tagging (e.g. dev, staging, production)"
  type        = string
}

variable "application_name" {
  description = "EMR Serverless application name"
  type        = string
}

variable "release_label" {
  description = "EMR release label (pins Spark version); update when Spark JAR target changes"
  type        = string
  default     = "emr-7.2.0-latest"
}

variable "artifact_bucket_arns" {
  description = "S3 bucket ARNs Spark jobs may read (artifacts + data prefixes)"
  type        = list(string)
  default     = []
}

variable "subnet_ids" {
  description = "Optional private subnet IDs for VPC jobs (leave empty for S3-only / public data path)"
  type        = list(string)
  default     = []
}

variable "security_group_ids" {
  description = "Security groups for EMR-created ENIs when using VPC (must pair with subnet_ids)"
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Extra resource tags"
  type        = map(string)
  default     = {}
}
