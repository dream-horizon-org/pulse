# ADR — Network (HTTP client) instrumentation

## Decision

Ship **OTLP trace spans** for outbound **Fetch** and **XMLHttpRequest** using upstream **`@opentelemetry/instrumentation-fetch`** + **`@opentelemetry/instrumentation-xml-http-request`**, augmented with Pulse **`pulse.type: network.<statusCode>`** (Android parity; missing status → `network.0`) and **stable HTTP semantic conventions** (`url.full`, `http.request.method`, `http.response.status_code`, `server.address`, `http.duration`, optional headers / peer.service / GraphQL attrs per [`network.md`](../v1/02-instrumentations/network.md)).

## Why no Plan A

Only one credible approach: **browser client spans** must reuse OTel’s patched `fetch`/`XHR` for context propagation and timing; reimplementing wrappers would duplicate upstream maintenance without benefit. No separate `PLAN-A-*.md`.

## Gate / config

- Remote: `PulseFeature.NETWORK_INSTRUMENTATION` (`network_instrumentation`), SDK `pulse_web_js`.
- Local: `instrumentations.network.enabled` (defaults on when gate allows).

## Deferred: `network_change`

`PulseFeature.NETWORK_CHANGE` (`network_change`) exists in `types/remote-config.ts` but is **not** wired in `NetworkInstrumentation`. Connection / online hints are stamped on the **resource** at SDK init (`resource.ts`), not as dynamic network-change spans or logs. Treat as intentional deferral until product defines browser online/offline signal shape.

## Grill

**Grill deferred:** Interactive `grill-me` session not run in agent loop; risks reviewed in PLAN-B (double `installAll`, consent denying `start()`, gate-off, OTLP URL ignored, shutdown order). Owner: implementer + PR reviewer.

## OTel spec deviations (intentional)

| Attribute | OTel spec | Pulse | Rationale |
|-----------|-----------|-------|-----------|
| `error.type` | Specific code string (`"404"`, `"500"`) | Class string (`"4xx"`, `"5xx"`, `"network_error"`, `"cors_error"`) | ClickHouse error-rate queries use class grouping across Android + Web. |
| `http.client.request.duration` metric | Stable Required histogram (seconds) | Not emitted; `http.duration` span attr (ms) used instead | Deferred — opt-in via `emitRequestDurationMetric` config flag (PLAN-C §P3.5). |

## Status

Accepted — implementation matches this ADR and PLAN-B. OTel spec gaps tracked in PLAN-C.
