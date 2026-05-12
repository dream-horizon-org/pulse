# vector — Plan Handbook

Vector configs that power the custom-events path: OTLP in → remap → S3 (Parquet) + ClickHouse usage metering.

Brief: [../../components/vector.md](../../components/vector.md)

## Files

- `vector/vector.yaml` — dev.
- `vector/vector-prod.yaml` — prod overrides.
- `vector/Dockerfile` — custom Vector image.

## Sub-files

| File | Topic |
|---|---|
| [sources.md](./config/sources.md) | OTLP gRPC + HTTP receivers; internal metrics |
| [transforms.md](./config/transforms.md) | `to_pulse_schema` remap; flattening of resource + attributes into columnar rows |
| [sinks.md](./config/sinks.md) | S3 Parquet sink (`s3_events`), ClickHouse sink (`clickhouse_project_events`), Prometheus exporter |

## Reading order

1. Brief.
2. `sources.md` → what comes in.
3. `transforms.md` → how we normalize.
4. `sinks.md` → where it goes.
5. For Athena queries on the S3 output: [../pulse-ingestion/collector/athena-ddl.md](../pulse-ingestion/collector/athena-ddl.md).

## Rebuild checklist

1. Start from the OpenTelemetry source (`type: opentelemetry`) with gRPC + HTTP bind.
2. Add `remap` transform matching the `to_pulse_schema` mapping — promote the standard resource/attribute keys to flat columns; keep the rest in a `props` map.
3. Add an S3 sink with Parquet encoding + disk buffer (Vector's durability guarantee).
4. Add a ClickHouse sink writing to `project_monthly_usage` (for usage billing).
5. Expose Prometheus metrics on `:9598` for ops.
6. Run under `deploy/docker-compose.yml`.
