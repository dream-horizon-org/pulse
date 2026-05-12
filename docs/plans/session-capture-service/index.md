# session-capture-service — plan

Component brief: [`docs/components/session-capture-service.md`](../../components/session-capture-service.md).

## Scope

Ingest browser session-recording payloads at high throughput with
bounded memory: accept gzip-compressed POSTs, decompress with explicit
size caps, parse JSON, and produce to Kafka with backpressure-aware
queue limits.

## Architecture sketch

```
Browser SDK
   |  POST /session-capture  (gzip, <=25 MiB)
   v
[Axum router] -> [decompress_payload] -> [recordings parser]
                                              |
                                              v
                                      [KafkaSink (FutureProducer)]
                                              |
                                              v
                                  Kafka: session_recording_events
```

Health: `/_liveness`, `/_readiness`, `/healthcheck`. Metrics: `/metrics`
(Prometheus).

## Sub-components

Core:

- [core/websocket-server.md](./core/websocket-server.md) — Axum HTTP
  ingest (named "websocket" in the brief; transport today is HTTP).
- [core/compression.md](./core/compression.md) — gzip decompression.
- [core/kafka-producer.md](./core/kafka-producer.md) — `KafkaSink`.

Ops:

- [ops/deployment.md](./ops/deployment.md)
- [ops/observability.md](./ops/observability.md)

## Cross-links

- Consumers: [`session-replay-ingestion`](../session-replay-ingestion/index.md),
  [`heatmap-screenshot-ingestion`](../heatmap-screenshot-ingestion/index.md).

## Risks

- Single 25 MiB body limit shared across all clients — DoS-able with
  many concurrent large bodies; rely on infra rate limit + request
  timeout.
- `rdkafka` cmake build complicates image size; mitigated by
  multi-stage `Dockerfile`.
- Kafka producer queue saturation manifests as 5xx — alert on
  `capture_kafka_producer_queue_depth` / `_limit`.
