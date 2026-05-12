# deployment

Parent: [session-capture-service](../index.md) ·
Brief: [component](../../../components/session-capture-service.md)

## 1. Purpose

How to build, ship, and scale the service.

## 2. Image

`backend/session-capture-service/Dockerfile`. Multi-stage Rust build;
final image contains the `pulse-session-capture` binary + librdkafka.
`rdkafka` uses `cmake-build` feature → builder stage needs `cmake`,
`libsasl2-dev`, `pkg-config`.

## 3. Runtime config

All via env (see `src/config.rs`):

- `PORT` (default 3400)
- `REQUEST_TIMEOUT_SECONDS`
- `KAFKA_*` (see [kafka-producer](../core/kafka-producer.md))

`.env` is auto-loaded by `dotenvy::dotenv()` in `main.rs` (dev only).

## 4. Probes

- Liveness: `GET /_liveness` (registry tracks `rdkafka` with 30 s TTL).
- Readiness: `GET /_readiness` (`health::readiness_handler`).
- Plain: `GET /healthcheck`.

## 5. Scaling

- Stateless → horizontal scale by replica count.
- CPU-bound on gzip decompression at peak; memory-bound by
  concurrent body size × concurrency.
- Kafka partition count must >= replica count for even distribution.

## 6. Resource shape (starting point)

CPU: 1 vCPU per replica; Memory: 512 MiB (room for one 25 MiB body in
flight × inbound concurrency). Tune via Prometheus.

## 7. Rollout

- Roll one replica at a time; readiness probe gates traffic.
- Drain via SIGTERM; tokio runtime stops `axum::serve` cleanly.

## 8. Cross-links

- [observability](./observability.md)
- [kafka-producer](../core/kafka-producer.md)

## 9. Open items

- Add HPA on `capture_kafka_producer_queue_depth`.
- Document edge proxy / WAF in front of `/session-capture`.
