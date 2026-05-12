# vector

Custom-events ingestion pipeline. SDKs send "custom events" (log signals carrying `pulse.type = custom_event` and arbitrary props) → Vector → S3 Parquet (queried by Athena) + ClickHouse `otel.project_monthly_usage` (for usage metering) + Prometheus exporter (self-observability).

Vector is built from a fork — `parth-modh/vector` branch `forked/multi-bucket` — to support per-event S3 bucket templating (`pulse-otel-{{ project_id }}`).

## Source layout

```
vector/
├── Dockerfile          (multi-stage Debian 12 build; clones the fork, runs `make build`)
├── vector.yaml         (216 lines; dev / docker-compose config)
└── vector-prod.yaml    (215 lines; prod config — different CH endpoint + creds)
```

Image entrypoint `/usr/bin/vector --config /etc/vector/vector.yaml`. Runs as uid 1000 (`vector`). Exposes API/health on `8686`; OTLP gRPC `4317`, OTLP HTTP `4318`; Prometheus on `9598`.

## Global

- `data_dir: /var/lib/vector` — required for the disk buffer on the S3 sink.
- `api.enabled: true` on `0.0.0.0:8686`.

## Sources

- `otlp_logs` (`type: opentelemetry`) — gRPC `0.0.0.0:4317` + HTTP `0.0.0.0:4318`. Vector is the OTLP endpoint for custom events; the OTel Collector (`backend/ingestion/`) handles the rest.
- `vector_metrics` (`type: internal_metrics`).

## Transforms

1. `to_pulse_schema` (remap) — flattens OTLP log into Pulse Parquet row:
   - Merges `resources` + `attributes` into `props_map` (attrs override resources), promotes the columns enumerated in `backend/ingestion/athena-otel-tables.sql`, JSON-encodes `props_map` into a catch-all `props` STRING.
   - Note: in `vector.yaml` `installation_id ← attrs."installation.id"`; in `vector-prod.yaml` it reads from `res."installation.id"`.
2. `add_counter` — `.event_count = 1`.
3. `aggregate_by_project` (reduce) — groups by `project_id`, flushes every 60 s, summing `event_count` (`ends_when: false`).
4. `format_for_clickhouse` — emits `{project_id, month=YYYY-MM-01, source="vector", event_count}` for the monthly-usage sink.

## Sinks

- `prometheus_exporter` ← `vector_metrics`, listens `0.0.0.0:9598/metrics`.
- `clickhouse_project_events` ← `format_for_clickhouse`. Writes `otel.project_monthly_usage`.
  - dev: `endpoint: http://clickhouse:8123`, user `pulse_user`.
  - prod: `endpoint: http://shard-1-replica-1.pulse-clickhouse.pulse.local:8123`, user `pulseuser`.
  - `skip_unknown_fields: true`, `batch.timeout_secs: 1`.
- `s3_events` ← `to_pulse_schema`. The production-grade sink:
  - `bucket: pulse-otel-{{ project_id }}` (templated — needs the fork), `region: ap-south-1`.
  - `key_prefix: vector-logs/%Y-%m-%d/%H/` — matches Athena partition projection (`date`, `hour`).
  - Disk buffer `max_size: 10 GiB`, `when_full: block` (backpressure).
  - Adaptive concurrency `initial 10, max 50`, request timeout 60 s.
  - Batch `max_bytes: 512 MiB`, `timeout_secs: 30` — target 150–500 MB Parquet files for compression.
  - Wrapper `compression: none`; encoding `parquet` with `infer_schema: true` and `compression: zstd` per column.

## Data flow

```
SDK (custom events)
   │ OTLP gRPC/HTTP
   ▼
Vector
   ├─ to_pulse_schema ──► s3_events ──► s3://pulse-otel-<project_id>/vector-logs/<date>/<hour>/*.parquet
   │                                              │
   │                                              ▼ Athena pulse_athena_db.otel_data_<project_id>
   └─ + counter + reduce + format ──► clickhouse otel.project_monthly_usage
```

## Sinks NOT present

Despite the brief's mention of a "MySQL sink", neither `vector.yaml` nor `vector-prod.yaml` defines one. MySQL writes happen exclusively through `pulse-server`.

## Operational notes

- Backpressure: if S3 fails for long enough to fill the 10 GiB disk buffer, the OTLP sources stop ack'ing — SDKs feel it as failed sends.
- Fork dependency: stock Vector cannot template the S3 bucket from event data. Rebuild from `parth-modh/vector#forked/multi-bucket` if you bump.
- Prod CH creds are committed to `vector-prod.yaml`. Rotate via secret injection, not by editing the file.

## Plans

`/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/vector/index.md`.

## Cross-links

- Athena DDL the sink must stay aligned with: `backend/ingestion/athena-otel-tables.sql` (see `docs/components/pulse-ingestion.md`).
- ClickHouse table written: `otel.project_monthly_usage` (`backend/db/dev/clickhouse/08_otel.project_monthly_usage.sql`).
- Bootstrap: `deploy/docker-compose.yml` service `vector`.
