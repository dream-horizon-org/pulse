# Vector · Transforms

How OTLP events become Pulse's flat custom-event schema.

Brief: [../../../components/vector.md](../../../components/vector.md) · Peers: [sources](./sources.md), [sinks](./sinks.md), [../../pulse-ingestion/collector/athena-ddl](../../pulse-ingestion/collector/athena-ddl.md).

## Source location

- Transform `to_pulse_schema` in `vector/vector.yaml`.
- Downstream transform `format_for_clickhouse` (feeds the ClickHouse usage sink).

## `to_pulse_schema`

Type: `remap`. Input: `otlp_logs.logs`.

VRL source (abridged):
```vrl
res = object(.resources) ?? {}
attrs = object(.attributes) ?? {}
props_map = merge(res, attrs)

. = {
  "event_name": .message,
  "project_id": res."project.id",
  "user_id": attrs."user.id",
  "installation_id": attrs."installation.id",
  "android_os_api_level": res."android.os.api_level",
  "os_version": res."os.version",
  "app_build_id": res."app.build_id",
  "app_build_name": res."app.build_name",
  "device_manufacturer": res."device.manufacturer",
  "device_model_identifier": res."device.model.identifier",
  "os_name": res."os.name",
  "service_name": res."service.name",
  "session_id": attrs."session.id",
  ...
}
```

## Purpose

1. Promote a stable set of identity / device / app columns from the nested OTLP maps so they become first-class Parquet columns (and Athena partition-pruneable fields).
2. Preserve the rest in `props_map` for flexible querying.
3. Provide a single canonical shape — consumed by both S3 (Parquet, for Athena) and ClickHouse (usage metering).

## Downstream shape

Consumer of this transform:
- `s3_events` sink writes Parquet files partitioned by date/hour under `s3://pulse-otel-<project>/vector-logs/YYYY-MM-DD/HH/`.
- `format_for_clickhouse` (separate transform) further shapes rows for the `project_monthly_usage` ClickHouse table — for billing/usage counters.

## Data contracts

- Output columns must match the Athena DDL in `backend/ingestion/athena-otel-tables.sql`.
- Missing keys are preserved as NULL (Parquet nullable columns).

## Rebuild recipe

1. Start from a `remap` transform consuming `otlp_logs.logs`.
2. Build a promotion list of every column Athena needs.
3. Spread resource + attr maps into the promoted columns; keep the rest in `props_map`.
4. Chain any downstream transforms (e.g. `format_for_clickhouse`) that need a narrower row.
