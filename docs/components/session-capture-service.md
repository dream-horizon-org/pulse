# session-capture-service

## What

High-throughput HTTP ingestion endpoint for browser session-recording
payloads (rrweb-style snapshot events). Accepts gzip-compressed JSON over
`POST /session-capture`, decompresses, validates and parses the recording
events, then produces them to a Kafka topic (default
`session_recording_events`) for downstream consumers (replay-ingestion,
heatmap-screenshot-ingestion) to fan out.

Adapted from PostHog's capture service decompression pipeline (see
header in `payload/decompression.rs`).

## Path + stack

- Path: `backend/session-capture-service/`
- Language: Rust 2021, crate `pulse-session-capture` v0.2.0
  (`Cargo.toml`).
- HTTP: `axum` 0.7 on `tokio` 1 (full features).
- Kafka producer: `rdkafka` 0.36 with `cmake-build` (librdkafka linked at
  build time, `FutureProducer`).
- Compression: `flate2` 1 (gzip; magic bytes `0x1f 0x8b 0x08`).
- Observability: `tracing` + `tracing-subscriber`, `metrics` +
  `metrics-exporter-prometheus`, `tower-http::trace` and CORS layer.
- IDs: `uuid` v7.
- Default port: `PORT=3400`.
- Default body limit: `RECORDING_BODY_SIZE = 25 MiB` (`router.rs`).

Note: although the brief calls this a WebSocket ingestor, the actual
transport implemented today is **HTTP POST** with gzip bodies. Update
this section if a WebSocket route is added.

## Build

```bash
cd backend/session-capture-service
cargo build --release
cargo run                       # uses .env via dotenvy
cargo test
```

Container: `Dockerfile`.

## Inputs + outputs

Inputs:

- `POST /session-capture` — gzip-compressed JSON body (≤ 25 MiB),
  containing batched recording events. Decoded via
  `payload::decompression::decompress_payload` (gzip auto-detected by
  magic bytes) and parsed in `payload/recordings.rs`.
- `GET /healthcheck`, `GET /_liveness`, `GET /_readiness` — health
  probes (`endpoint::healthcheck`, `health::readiness_handler`,
  `HealthRegistry::get_status`).
- `GET /metrics` — Prometheus scrape.

Outputs:

- Kafka topic from `KafkaConfig.kafka_topic` (env `KAFKA_TOPIC`); one
  Kafka record per replay event, keyed/partitioned for session
  affinity. `acks`, `linger_ms`, `compression.codec`,
  `message.max.bytes`, `queue.buffering.max.kbytes` are all
  env-configurable (see `config.rs`).
- Prometheus metrics (e.g. `capture_kafka_callback_queue_depth`,
  `capture_kafka_producer_queue_depth`,
  `capture_kafka_any_brokers_down`, `capture_raw_payload_size`,
  per-topic `capture_kafka_produce_avg_batch_size_bytes`).

## Key files

- `src/main.rs` — tokio entrypoint, tracing init, builds `KafkaSink`,
  registers `rdkafka` liveness probe (`Duration::from_secs(30)`),
  binds Axum router.
- `src/router.rs` — route table, body-size limit, CORS, timeout,
  metrics middleware, `TraceLayer`.
- `src/endpoint.rs` — `capture` handler (entry: `handle_recording_payload`
  → `recordings::process_replay_events`).
- `src/payload/decompression.rs` — `decompress_payload(bytes, limit)`
  with size guard.
- `src/payload/recordings.rs` — JSON parsing into recording events.
- `src/events/recordings.rs` — `process_replay_events(sink, events)` →
  drives Kafka producer.
- `src/sinks/kafka.rs` — `KafkaSink` (`FutureProducer`), stats callback
  pushes broker / queue gauges via `metrics::gauge!`.
- `src/health.rs` — `HealthRegistry` + per-component liveness handles.
- `src/metrics_middleware.rs` — request timing + `apply_request_timeout`.
- `src/config.rs` — `Config::from_env` + `KafkaConfig` (port 3400 default,
  all Kafka knobs via env).
- `src/prometheus.rs` — exponential histogram buckets for latency +
  payload sizes.

## Owners

- Owner: _TBD_
- Backup: _TBD_

## Plan

See [`docs/plans/session-capture-service/index.md`](../plans/session-capture-service/index.md).
