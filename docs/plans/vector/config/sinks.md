# Vector · Sinks

Where Vector sends data.

Brief: [../../../components/vector.md](../../../components/vector.md) · Peers: [sources](./sources.md), [transforms](./transforms.md).

## Sinks

### `s3_events` — Parquet into per-project buckets

```yaml
s3_events:
  type: aws_s3
  inputs: [to_pulse_schema]
  bucket: "pulse-otel-{{ project_id }}"
  region: "ap-south-1"
  key_prefix: "vector-logs/%Y-%m-%d/%H/"
  buffer:
    type: disk
    max_size: 10737418240   # 10 GB
    when_full: block
  request:
    concurrency: adaptive
    timeout_secs: 60
    adaptive_concurrency:
      max_concurrency_limit: 50
      initial_concurrency: 10
  batch:
    max_bytes: 536870912    # 512 MB
    timeout_secs: 30
  compression: none
  encoding:
    codec: parquet
    parquet:
      infer_schema: true
      compression: zstd
```

- Per-project bucket for tenant isolation (`pulse-otel-{{ project_id }}`).
- Large batches (~512MB uncompressed → ~50–150MB Parquet with zstd) to keep files cheap for Athena.
- Disk buffer + `when_full: block` gives backpressure without data loss.

### `clickhouse_project_events` — usage metering

```yaml
clickhouse_project_events:
  type: clickhouse
  inputs: [format_for_clickhouse]
  endpoint: "http://clickhouse:8123"
  database: otel
  table: project_monthly_usage
  auth: { strategy: basic, user: pulse_user, password: pulse_password }
  skip_unknown_fields: true
  batch: { timeout_secs: 1 }
```

- Writes per-project, per-event counts for billing (`project_monthly_usage` is a ClickHouse rollup table).

### `prometheus_exporter` — ops

Exposes Vector's internal metrics on `:9598`.

## Rebuild recipe

1. Define the S3 sink first; ensure the `data_dir` + bucket exist.
2. Add the ClickHouse sink only after the `project_monthly_usage` table is created (via `backend/db/{dev,prod}/clickhouse/`).
3. Add the Prometheus exporter for ops; scrape from your monitoring stack.
4. Validate end-to-end: post an OTLP event, expect a Parquet file in S3 within ~30s (batch timeout) and a row in `project_monthly_usage` within ~1s.
