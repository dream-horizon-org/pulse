output "application_id" {
  description = "Pass to pulse-server CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_APPLICATION_ID"
  value       = aws_emrserverless_application.analytics.id
}

output "application_arn" {
  value = aws_emrserverless_application.analytics.arn
}

output "job_role_arn" {
  description = "Pass to pulse-server CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_JOB_ROLE_ARN"
  value       = aws_iam_role.emr_job.arn
}

output "execution_role_arn" {
  description = "Pass to pulse-server CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_EXECUTION_ROLE_ARN when EMR is enabled"
  value       = aws_iam_role.emr_execution.arn
}

output "pulse_server_policy_snippet" {
  description = "Attach to pulse-server EC2/ECS task role (tighten Resource to this application ARN)"
  value       = <<-EOT
    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": [
            "emr-serverless:StartJobRun",
            "emr-serverless:GetJobRun",
            "emr-serverless:ListJobRuns",
            "emr-serverless:CancelJobRun"
          ],
          "Resource": "${aws_emrserverless_application.analytics.arn}"
        },
        {
          "Effect": "Allow",
          "Action": "iam:PassRole",
          "Resource": "${aws_iam_role.emr_job.arn}"
        }
      ]
    }
  EOT
}
