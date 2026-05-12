# observability

Parent: [session-capture-service](../index.md) ·
Brief: [component](../../../components/session-capture-service.md)

## 1. Purpose

What we measure, where it ships, and what alerts to wire.

## 2. Tracing

- `tracing` + `tracing-subscriber` with `EnvFilter`
  (`RUST_LOG`, default `pulse_session_capture=info`).
- `tower_http::trace::TraceLayer::new_for_http()` on the router.
- `#[instrument]` on `capture` handler and `decompress_payload`.

## 3. Metrics

- Exporter: `metrics_exporter_prometheus` (`src/prometheus.rs`).
- Scraped at `GET /metrics`.
- Histogram buckets: `EXPONENTIAL_SECONDS` for latency,
  `PAYLOAD_SIZES` for body sizes (100 B → 10 MB).

### Key series

| Series                                       | Type      | Source             |
| -------------------------------------------- | --------- | ------------------ |
| `capture_raw_payload_size`                   | histogram | decompression      |
| `capture_kafka_producer_queue_depth`         | gauge     | KafkaContext stats |
| `capture_kafka_producer_queue_depth_limit`   | gauge     | KafkaContext stats |
| `capture_kafka_producer_queue_bytes`         | gauge     | KafkaContext stats |
| `capture_kafka_callback_queue_depth`         | gauge     | KafkaContext stats |
| `capture_kafka_any_brokers_down`             | gauge     | KafkaContext stats |
| `capture_kafka_produce_avg_batch_size_bytes` | gauge     | per-topic          |

## 4. Health

- `/_liveness` — `HealthRegistry` with `rdkafka` handle (30 s TTL).
  Healthy iff at least one broker reported UP in last 30 s.
- `/_readiness` — `health::readiness_handler` (process ready, sink
  constructed).

## 5. Logging

JSON via `tracing-subscriber` fmt layer (configure for prod). Errors
log `CaptureError` variants; PII in request bodies must not be
logged.

## 6. Alerts (suggested)

- `capture_kafka_any_brokers_down > 0` for 2 m.
- `capture_kafka_producer_queue_depth / _limit > 0.8` for 5 m.
- p99 request latency > N (per histogram bucket).
- 5xx rate > 1% (request middleware).

## 7. Dashboards

Single dashboard with: RPS, p50/p95/p99 latency, body-size histogram,
Kafka queue gauges, broker-up count, decompression error rate.

## 8. Cross-links

- [http-ingest](../core/websocket-server.md)
- [kafka-producer](../core/kafka-producer.md)
- [deployment](./deployment.md)

## 9. Open items

- Wire OTLP exporter for traces if/when Pulse self-monitoring lands.
- Add per-`pulse.type` counter so we can correlate ingest mix with
  downstream consumer pressure.
