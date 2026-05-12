# Collector — config reference

## Purpose

Receive OTLP HTTP, batch it, fan it out to ClickHouse and (selectively) to `pulse-server`. Expose health + Prometheus telemetry.

## Source

`backend/ingestion/otel-collector.yaml`.

## Receivers

```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318
```

Only OTLP HTTP. gRPC is intentionally omitted; gRPC traffic for custom events terminates at Vector.

## Extensions

```yaml
extensions:
  health_check:
    endpoint: 0.0.0.0:13133
```

The commented-out `file_storage` block is the hook for enabling exporter persistent queues — not currently enabled.

## Processors

```yaml
processors:
  batch:
    send_batch_size:     50000
    timeout:             1s
    send_batch_max_size: 75000
```

One processor — `batch`. No attribute manipulation here; SDKs are responsible for emitting the right resource/log/span attributes.

## Exporters

### `clickhouse`

```yaml
clickhouse:
  endpoint: ${CLICKHOUSE_ENDPOINT}
  database: ${CLICKHOUSE_DATABASE}
  username: ${CLICKHOUSE_USER}
  password: ${CLICKHOUSE_PASSWORD}
  create_schema: false
  traces_table_name: otel_traces
  logs_table_name:   otel_logs
  metrics_tables:
    gauge:                  { name: otel_metrics_gauge }
    sum:                    { name: otel_metrics_sum }
    summary:                { name: otel_metrics_summary }
    histogram:              { name: otel_metrics_histogram }
    exponential_histogram:  { name: otel_metrics_exp_histogram }
  async_insert: false
  timeout: 10s
  retry_on_failure:
    enabled: true
    initial_interval: 5s
    max_interval: 30s
    max_elapsed_time: 300s
```

Env vars are set in `deploy/.env` and forwarded by Compose. `create_schema: false` is critical — the schema is pre-loaded by `deploy/scripts/init-clickhouse.sh`.

### `otlphttp/pulse`

```yaml
otlphttp/pulse:
  endpoint: http://pulse-server:8080
  tls:
    insecure: true
  compression: gzip
  retry_on_failure:
    enabled: false
  sending_queue:
    enabled: true
    queue_size: 1000
```

Used only for the crash/ANR/non-fatal log subset routed by `routing/pulse`. Retries disabled to keep the backend from being hammered when it's degraded; the small in-memory queue absorbs spikes.

## Service / telemetry

```yaml
telemetry:
  logs:    { level: info }
  metrics: { level: detailed, readers: [{ pull: { exporter: { prometheus: { host: 0.0.0.0, port: 8888 } } } }] }
```

Prometheus scrape at `:8888/metrics`. Health at `:13133/`.

## Inputs

Mobile + Web SDKs over OTLP HTTP, via `LOGS_COLLECTOR_URL` / `METRIC_COLLECTOR_URL` / `SPAN_COLLECTOR_URL` returned in the SDK config (`pulse_sdk_configs` row).

## Outputs

- ClickHouse tables (see `pulse-db/clickhouse/otel-tables.md`).
- `pulse-server` `/v1/logs` (effectively — via OTLP HTTP path on 8080).

## Operational notes

- Tune `batch.send_batch_size` upward if CH ingestion is the bottleneck; downward if memory pressure on the Collector becomes visible.
- Enabling `file_storage` + `sending_queue.storage` gives durability across Collector restarts; budget disk for it.

## Failure modes

- CH down → `clickhouse` exporter retries up to 300 s, then drops. No persistent queue currently.
- `pulse-server` down → `otlphttp/pulse` retries disabled; messages buffered in the 1000-slot queue, then dropped.
- Schema drift between SDK attribute names and ClickHouse materialized expressions → silent empty columns. Validated in the Web/Android SDK e2e specs.

## Related

- `collector/pipelines.md` — how these exporters are wired.
- `collector/athena-ddl.md` — the Parquet side (Vector path).
