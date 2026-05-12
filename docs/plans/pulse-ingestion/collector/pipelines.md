# Collector Pipelines

How `backend/ingestion/otel-collector.yaml` wires sources (OTLP in) to destinations (ClickHouse, S3 via Vector, logs).

Brief: [../../../components/pulse-ingestion.md](../../../components/pulse-ingestion.md) · Peers: [otel-collector-config](./otel-collector-config.md), [athena-ddl](./athena-ddl.md).

## Source location

- `backend/ingestion/otel-collector.yaml`

## Pipeline wiring

Receivers:
- `otlp`: HTTP on `0.0.0.0:4318`. (gRPC optionally added in prod.)

Extensions:
- `health_check` on `0.0.0.0:13133` for liveness.

Processors:
- `batch`: `send_batch_size: 50000`, `timeout: 1s`, `send_batch_max_size: 75000`.

Exporters:
- `clickhouse`: driven by `${CLICKHOUSE_*}` env. Writes to:
  - `otel_traces`
  - `otel_logs`
  - `otel_metrics_gauge`, `otel_metrics_sum`, `otel_metrics_summary`, `otel_metrics_histogram`, `otel_metrics_exp_histogram`
  - `async_insert: false`, `timeout: 10s`, retry with exponential backoff.

Service pipelines (declared at the bottom of the YAML):
- `traces`: `otlp → batch → clickhouse`
- `logs`: `otlp → batch → clickhouse`
- `metrics`: `otlp → batch → clickhouse`

## Data contracts

Tables must already exist (created by `pulse-db` migrations; `create_schema: false`). See [../../pulse-db/clickhouse/otel-tables.md](../../pulse-db/clickhouse/otel-tables.md) for schema.

## Tests

Smoke test: post an OTLP batch to `:4318`; confirm row count in `otel_traces` via `clickhouse-client`.

## History / decisions

- `create_schema: false` because schema is owned by `backend/db/{dev,prod}/clickhouse/` migrations — ensures deterministic column ordering.
- `async_insert: false` to surface write failures synchronously (the Collector's retry queue handles durability).

## Rebuild recipe

1. Build Collector image including `clickhouse` exporter (contrib distribution).
2. Mount the YAML at `/etc/otel-collector.yaml`.
3. Provide CH env vars via docker-compose or k8s secret.
4. Expose `:4318` (HTTP) and `:13133` (health).
