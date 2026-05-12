# http-ingest (named "websocket-server")

Parent: [session-capture-service](../index.md) ·
Brief: [component](../../../components/session-capture-service.md)

## 1. Purpose

Terminate HTTP from browser SDKs, enforce body limits + request
timeout, route to capture/health/metrics handlers.

Note: this sub-component is labeled "websocket-server" in the original
brief; the implementation is **HTTP POST** via Axum, not WebSocket.
Rename if/when a WS route is added.

## 2. Source

- `src/main.rs` — tokio bootstrap, env init, listener bind.
- `src/router.rs` — `create_router(State, ...)`, `RECORDING_BODY_SIZE =
  25 * 1024 * 1024`, CORS, `TraceLayer`, metrics middleware.
- `src/endpoint.rs` — `capture` handler.
- `src/extractors.rs` — request body extraction.
- `src/metrics_middleware.rs` — `track_metrics`, `apply_request_timeout`.

## 3. Routes

| Method | Path                | Handler                              |
| ------ | ------------------- | ------------------------------------ |
| POST   | `/session-capture`  | `endpoint::capture`                  |
| GET    | `/healthcheck`      | `endpoint::healthcheck`              |
| GET    | `/_liveness`        | `liveness.get_status`                |
| GET    | `/_readiness`       | `health::readiness_handler`          |
| GET    | `/metrics`          | `metrics_handle.render` (Prometheus) |

## 4. Limits

- Body: 25 MiB via `DefaultBodyLimit::max(RECORDING_BODY_SIZE)`.
- Request timeout: `REQUEST_TIMEOUT_SECONDS` env (applied by
  `apply_request_timeout`).
- Per-chunk read timeout: `body_chunk_read_timeout_seconds`.
- Read chunk size: `body_read_chunk_size_kb` (config).

## 5. CORS

`tower_http::cors::CorsLayer` — `Any` origin, methods
`POST,OPTIONS`, headers `Any`. Tighten if endpoint is exposed without
an edge proxy.

## 6. Errors

`CaptureError` (in `src/api.rs`) implements `IntoResponse` to map
parse / decompression / Kafka failures to HTTP status codes.

## 7. Tests

`cargo test` for handler-level coverage; integration tests live
alongside modules.

## 8. Cross-links

- [compression](./compression.md)
- [kafka-producer](./kafka-producer.md)
- [observability](../ops/observability.md)

## 9. Open items

- Decide whether to add a real WebSocket transport for very long
  sessions (avoid POST batching delay).
- Tighten CORS allow-list per environment.
