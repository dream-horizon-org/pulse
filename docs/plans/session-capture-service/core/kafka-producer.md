# kafka-producer

Parent: [session-capture-service](../index.md) ·
Brief: [component](../../../components/session-capture-service.md)

## 1. Purpose

Buffer and asynchronously produce recording events to Kafka with
bounded queue depth, configurable acks/linger, and live broker
liveness reporting.

## 2. Source

- `src/sinks/mod.rs` — `Event` trait (the sink interface).
- `src/sinks/kafka.rs` — `KafkaSink`, `KafkaContext` (stats callback).
- `src/events/recordings.rs` — `process_replay_events(sink, events)`.
- `src/config.rs` — `KafkaConfig` fields.

## 3. Config (env)

| Field                                       | Notes                       |
| ------------------------------------------- | --------------------------- |
| `kafka_hosts`                               | bootstrap servers           |
| `kafka_topic`                               | destination topic           |
| `kafka_producer_linger_ms`                  | batch wait                  |
| `kafka_producer_queue_mib`                  | total buffer (MiB)          |
| `kafka_producer_message_max_bytes`          | single-record cap           |
| `kafka_compression_codec`                   | snappy/gzip/lz4/zstd/none   |
| `kafka_producer_max_retries`                | producer-level retries      |
| `kafka_producer_acks`                       | `0` / `1` / `all`           |
| `kafka_message_timeout_ms`                  | per-message timeout         |
| `kafka_metadata_max_age_ms`                 | metadata refresh            |
| `kafka_topic_metadata_refresh_interval_ms`  | topic refresh               |
| `kafka_socket_timeout_ms`                   | broker socket               |
| `kafka_tls`                                 | bool                        |
| `kafka_client_id`                           | identifier                  |

## 4. Liveness

`KafkaContext::stats` is called periodically by `rdkafka`. Any broker
in state `UP` calls `liveness.report_healthy_blocking()` — feeds the
`/_liveness` registry with TTL `Duration::from_secs(30)` (from
`main.rs`).

## 5. Metrics emitted (`metrics::gauge!`)

- `capture_kafka_callback_queue_depth`
- `capture_kafka_producer_queue_depth` / `_limit`
- `capture_kafka_producer_queue_bytes` / `_limit`
- `capture_kafka_any_brokers_down`
- Per topic: `capture_kafka_produce_avg_batch_size_bytes`,
  `capture_kafka_produce_avg_batch_size_events`.

## 6. Send path

`process_replay_events` calls `sink.send(...)` → `FutureProducer::send`
with a `Timeout`. On producer queue full → `CaptureError` → HTTP 5xx
to client → SDK retries.

## 7. Failure modes

- All brokers down → `_liveness` flips unhealthy within 30 s → k8s
  restart.
- Queue full → 5xx surfaced; client backoff is the safety valve.
- Partition skew → addressed by record key choice in
  `events::recordings`.

## 8. Tests

`cargo test` — unit on `KafkaContext` stats parsing; integration
against a local Kafka in compose.

## 9. Cross-links

- [http-ingest](./websocket-server.md)
- [compression](./compression.md)
- [observability](../ops/observability.md)
- Downstream: [session-replay-ingestion](../../session-replay-ingestion/index.md),
  [heatmap-screenshot-ingestion](../../heatmap-screenshot-ingestion/index.md).
