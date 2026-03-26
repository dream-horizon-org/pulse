# SparkJobService API Documentation

## Methods

### 1. submitJob
**Signature**: `Single<SparkJobResponse> submitJob(SparkJobRequest request)`

**Parameters**:
- `request` - SparkJobRequest containing job configuration

**Returns**: `Single<SparkJobResponse>` - Job submission response

### 2. getJobStatus
**Signature**: `Single<GetJobRunResponse> getJobStatus(String jobRunId)`

**Parameters**:
- `jobRunId` - EMR job run ID

**Returns**: `Single<GetJobRunResponse>` - EMR job status response

## Request Model: SparkJobRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `jobName` | `String` | Yes | Job name |
| `entryPoint` | `String` | Yes | Main artifact URI (e.g. `s3://bucket/app.jar` or PySpark script) — maps to EMR `SparkSubmit.entryPoint` |
| `mainClass` | `String` | No | FQCN for Java/Scala; sent as `--class` in `sparkSubmitParameters`. Omit for PySpark if unused |
| `arguments` | `List<String>` | No | Passed to EMR `entryPointArguments` |
| `sparkSubmitParameters` | `String` | No | Additional `spark-submit` flags (`--conf`, `--jars`, etc.). Must **not** include `--class` (use `mainClass`) |
| `timeoutMinutes` | `Long` | No | Execution timeout |
| `tags` | `Map<String, String>` | No | Job tags |

## Response Model: SparkJobResponse

| Field | Type | Description |
|-------|------|-------------|
| `applicationId` | `String` | EMR application ID |
| `jobRunId` | `String` | EMR job run ID |
| `arn` | `String` | Job run ARN |
| `jobName` | `String` | Job name |
| `entryPoint` | `String` | Echo of submitted main artifact URI |
| `mainClass` | `String` | Echo of submitted main class, if any |
| `submittedAt` | `LocalDateTime` | Submission time |

**Sample Response**:
```json
{
  "applicationId": "00f4lf2226mop709",
  "jobRunId": "00f4lf2226mop70a",
  "arn": "arn:aws:emr-serverless:us-east-1:123456789012:applications/00f4lf2226mop709/jobruns/00f4lf2226mop70a",
  "jobName": "Daily Funnel Processing",
  "entryPoint": "s3://artifacts/jobs/funnel.jar",
  "mainClass": "com.pulse.batch.FunnelProcessor",
  "submittedAt": "2026-03-25T10:30:15.123"
}
```

## GetJobRunResponse (EMR SDK)

| Field | Type | Description |
|-------|------|-------------|
| `jobRun.jobRunId` | `String` | Job run ID |
| `jobRun.applicationId` | `String` | Application ID |
| `jobRun.state` | `JobRunState` | Job state (SUBMITTED, PENDING, RUNNING, SUCCESS, FAILED, etc.) |
| `jobRun.createdAt` | `Instant` | Creation time |
| `jobRun.updatedAt` | `Instant` | Last update time |

**Sample Response**:
```json
{
  "jobRun": {
    "jobRunId": "00f4lf2226mop70a",
    "applicationId": "00f4lf2226mop709",
    "arn": "arn:aws:emr-serverless:us-east-1:123456789012:applications/00f4lf2226mop709/jobruns/00f4lf2226mop70a",
    "name": "Daily Funnel Processing",
    "state": "RUNNING",
    "stateDetails": "Job is currently running",
    "createdAt": "2026-03-25T10:30:15.123Z",
    "updatedAt": "2026-03-25T10:32:45.678Z",
    "executionTimeoutMinutes": 60,
    "jobDriver": {
      "sparkSubmit": {
        "entryPoint": "s3://artifacts/jobs/funnel.jar",
        "entryPointArguments": ["--date", "2026-03-25"],
        "sparkSubmitParameters": "--class com.pulse.batch.FunnelProcessor --conf spark.sql.adaptive.enabled=true"
      }
    }
  }
}
```
