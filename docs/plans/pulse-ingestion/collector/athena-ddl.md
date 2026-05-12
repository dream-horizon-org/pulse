# Athena DDL (custom events over S3)

Athena table definitions for the custom-events path (SDKs → Vector → S3 Parquet → Athena).

Brief: [../../../components/pulse-ingestion.md](../../../components/pulse-ingestion.md) · Peers: [../../vector/config/sinks](../../vector/config/sinks.md), [pipelines](./pipelines.md).

## Source location

- `backend/ingestion/athena-otel-tables.sql`

## Purpose

The telemetry path goes to ClickHouse. Custom events (arbitrary user-defined events) go through Vector to S3 in Parquet format and are queried via Athena. This DDL file declares the external tables over those S3 prefixes.

## Public surface

External tables declared in the file (one per signal kind / partition scheme). Each is `CREATE EXTERNAL TABLE IF NOT EXISTS ... STORED AS PARQUET LOCATION 's3://<bucket>/<prefix>/' TBLPROPERTIES ('projection.enabled' = 'true', ...)`.

Typical partitions: `project_id`, `date` (yyyy-mm-dd).

## Internal design

- Tables are Athena external; no data movement on register.
- Partition projection enabled to avoid `MSCK REPAIR TABLE`.
- Schema matches Vector's output (see [../../vector/config/transforms.md](../../vector/config/transforms.md) for the `to_pulse_schema` transform that builds the flattened rows).

## Data contracts

Flattened columns include: `event_name`, `project_id`, `user_id`, `installation_id`, `android_os_api_level`, `os_version`, `app_build_id`, `app_build_name`, `device_manufacturer`, `device_model_identifier`, `os_name`, `service_name`, `session_id`, plus a `props` map for free-form attrs.

## Tests

Manual: run the DDL in Athena console; `SELECT COUNT(*) FROM events WHERE date = '<yesterday>'` should return a non-zero count.

## History / decisions

Parquet + partition projection chosen to minimize Athena scan cost; JSON/CSV variants were prototyped and rejected for cost.

## Rebuild recipe

1. Create an S3 bucket + prefix layout matching `{project_id}/{date}/`.
2. Point Vector at the bucket (see [../../vector/config/sinks.md](../../vector/config/sinks.md)).
3. Run `athena-otel-tables.sql` in the target AWS account.
4. Validate with a test event; confirm Athena returns the row.
