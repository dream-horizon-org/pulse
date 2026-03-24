# EMR Serverless (Pulse analytics batch)

Terraform module for an **EMR Serverless Spark application**, **job runtime IAM role**, and **execution IAM role** used by future funnel/journey batch jobs. Default region is **ap-south-1**.

## When to use

- **Repeatable** staging/production deployments (aligns with other stacks under `deploy/terraform/`).
- You may create the first environment **manually in the AWS Console**; use this module when you want **IaC parity** or new environments.

## Prerequisites

- Terraform >= 1.3, AWS provider ~> 6.0
- Set `artifact_bucket_arns` to the S3 buckets/prefix ARNs your Spark jobs read (can be empty for an initial apply; add policies later).

## Example

```hcl
module "emr_analytics" {
  source = "./emr-serverless" # or git/module path

  environment      = "dev"
  application_name = "pulse-analytics-batch"
  release_label    = "emr-7.2.0-latest"

  artifact_bucket_arns = [
    "arn:aws:s3:::pulse-analytics-artifacts-dev",
  ]
}
```

## After apply

1. Set pulse-server env (or secrets):

   - `CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_ENABLED=true`
   - `CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_APPLICATION_ID` = `application_id` output
   - `CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_JOB_ROLE_ARN` = `job_role_arn` output
   - Optional: `CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_EXECUTION_ROLE_ARN` = `execution_role_arn` output

2. Attach the JSON from output `pulse_server_policy_snippet` to the **pulse-server** IAM role (ECS task role or EC2 instance profile), or merge equivalent statements into your existing policy.

3. **`iam:PassRole`** must allow only the **job** role ARN (principle of least privilege).

## Importing console-created resources

If you created roles or an application manually, use `terraform import` on `aws_emrserverless_application.analytics`, `aws_iam_role.emr_job`, and `aws_iam_role.emr_execution` after matching addresses to your module. Adjust `application_name` / `environment` so resource names do not collide.

## VPC / ClickHouse (later)

When jobs must reach private ClickHouse, set `subnet_ids` and `security_group_ids` and ensure routes + security groups allow traffic to ClickHouse. See `docs/architecture/funnel-user-journey-hld.md` for batch flow context.

## Backend

This module does **not** define an S3 backend. Copy the `terraform { backend "s3" { ... } }` pattern from `deploy/terraform/pulse-server/main.tf` when wiring state for an environment.
