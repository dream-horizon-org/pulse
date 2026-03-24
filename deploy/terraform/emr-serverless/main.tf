data "aws_caller_identity" "current" {}

# Role assumed by Spark jobs (passed to StartJobRun as job driver role).
resource "aws_iam_role" "emr_job" {
  name = "${var.application_name}-job-${var.environment}"
  tags = var.tags

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "emr-serverless.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "emr_job_data" {
  count = length(var.artifact_bucket_arns) > 0 ? 1 : 0
  name  = "${var.application_name}-job-s3-${var.environment}"
  role  = aws_iam_role.emr_job.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "S3ArtifactsAndData"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
        ]
        Resource = flatten([
          var.artifact_bucket_arns,
          [for a in var.artifact_bucket_arns : "${a}/*"],
        ])
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/emr-serverless/*"
      }
    ]
  })
}

# Role used by the EMR Serverless service for the application.
resource "aws_iam_role" "emr_execution" {
  name = "${var.application_name}-exec-${var.environment}"
  tags = var.tags

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "emr-serverless.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

# Customer-managed execution role (not the service-linked role policy, which is restricted to AWS).
resource "aws_iam_role_policy" "emr_execution" {
  name = "${var.application_name}-exec-policy-${var.environment}"
  role = aws_iam_role.emr_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "VpcAndLogsForEmrServerless"
        Effect = "Allow"
        Action = [
          "ec2:CreateNetworkInterface",
          "ec2:DeleteNetworkInterface",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DescribeSecurityGroups",
          "ec2:DescribeSubnets",
          "ec2:DescribeVpcs",
          "ec2:DescribeDhcpOptions",
          "ec2:DescribeRouteTables",
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "*"
      },
      {
        Sid      = "CloudWatchMetrics"
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = "*"
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = ["AWS/EMRServerless", "AWS/Usage"]
          }
        }
      }
    ]
  })
}

resource "aws_emrserverless_application" "analytics" {
  name          = var.application_name
  release_label = var.release_label
  type          = "SPARK"
  tags          = var.tags

  execution_role_arn = aws_iam_role.emr_execution.arn

  dynamic "network_configuration" {
    for_each = length(var.subnet_ids) > 0 ? [1] : []
    content {
      subnet_ids         = var.subnet_ids
      security_group_ids = var.security_group_ids
    }
  }
}
