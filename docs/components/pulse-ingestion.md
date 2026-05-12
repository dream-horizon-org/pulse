# pulse-ingestion

OTel Collector (contrib distribution) plus Athena DDL. The collector receives OTLP from SDKs and fans signals into ClickHouse and, for matched logs, back into `pulse-server`. Athena queries Parquet event files written by `vector/` (see `docs/components/vector.md`).

## Source layout

```
backend/ingestion/
├── otel-collector.yaml      (113 lines; full Collector config)
└── athena-otel-tables.sql   (Athena external-table DDL template)
```

Container runs the upstream `otel/opentelemetry-collector-contrib` image with this YAML mounted in; see `deploy/docker-compose.yml` service `otel-collector`.

## Pipelines

Receivers, single OTLP listener (HTTP only — gRPC is intentionally not enabled here; gRPC for "custom events" goes to Vector):
- `otlp` → `0.0.0.0:4318`

Processors:
- `batch` (`send_batch_size: 50000`, `timeout: 1s`, `send_batch_max_size: 75000`)

Exporters:
- `clickhouse` (driven by `CLICKHOUSE_ENDPOINT`, `CLICKHOUSE_DATABASE`, `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD` env vars). `create_schema: false` — schema is preloaded by `init-clickhouse.sh`. Tables:
  - `traces_table_name: otel_traces`
  - `logs_table_name: otel_logs`
  - `metrics_tables`: `gauge → otel_metrics_gauge`, `sum → otel_metrics_sum`, `summary → otel_metrics_summary`, `histogram → otel_metrics_histogram`, `exponential_histogram → otel_metrics_exp_histogram`
  - `async_insert: false`, 10 s timeout, retry 5 s → 30 s → cap 300 s
- `otlphttp/pulse` → `http://pulse-server:8080`, gzip, sending-queue 1000, retries disabled (fire-and-forget into the app).

Connector `routing/pulse` (log-context):
- Default → `logs/to-clickhouse`.
- Match `attributes["pulse.type"] in {"device.anr","device.crash","non_fatal"}` → `logs/to-backend`.

Pipelines (final wiring):
- `traces`:  otlp → batch → clickhouse
- `metrics`: otlp → batch → clickhouse
- `logs/in`: otlp → routing/pulse
- `logs/to-backend`:    routing/pulse → batch → otlphttp/pulse
- `logs/to-clickhouse`: routing/pulse → batch → clickhouse

Extensions: `health_check` on `0.0.0.0:13133`. Telemetry: Prometheus metrics on `0.0.0.0:8888`, internal logs at `info`.

## Athena DDL

`athena-otel-tables.sql` creates database `pulse_athena_db` and a template external table:

```
pulse_athena_db.otel_data_{PROJECT_ID}
  LOCATION 's3://pulse-otel-{PROJECT_ID}/vector-logs/'
  PARTITIONED BY (date STRING, hour STRING)
  STORED AS PARQUET
```

Project isolation is by **bucket** (`pulse-otel-<project_id>`). Partition projection is enabled (`projection.enabled = true`) with `date` range `2024-01-01,NOW` and `hour` `0..23`, so `MSCK REPAIR` is never needed.

Promoted Parquet columns (must match Vector's `to_pulse_schema` remap):
```
event_name, project_id, user_id, installation_id, android_os_api_level,
os_version, app_build_id, app_build_name, device_manufacturer,
device_model_identifier, os_name, service_name, session_id, screen_name,
network_carrier_mcc/mnc/icc, pulse_app_state, span_id, trace_id,
timestamp, vector_observed_timestamp, scope_name, flags, props (JSON catch-all)
```

`{PROJECT_ID}` is substituted by `backend/server` when provisioning Athena tables for a project (job tracked in MySQL `athena_job`).

## Data contract reminder

All signals must carry the materialized attributes ClickHouse extracts (`project.id`, `pulse.type`, `os.name`/`os.type`, `app.build_name`, `session.id`, `user.id`, `app.installation.id`). The collector itself does not enrich or rewrite — SDKs are responsible.

## Plans

`/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/pulse-ingestion/index.md`.

## Cross-links

- Inputs: `pulse-android-otel/`, `pulse-react-native-otel/`, `pulse-web-otel/` (OTLP HTTP 4318).
- Outputs: ClickHouse `otel.*` (see `docs/components/pulse-db.md`), `pulse-server:8080` for crash/ANR/non-fatal log routing.
- Adjacent: `vector/` handles the gRPC/custom-events path to S3 Parquet.
