# Phase 0 — Research: HTTP client instrumentation (ecosystem / industry)

## Signal type

**Trace spans** (`SpanKind.CLIENT`), not logs or metrics — matches Android OkHttp instrumentation, OTel HTTP semantic conventions, and ClickHouse rollups (`pulse.type LIKE 'network.%'`). Web emits `pulse.type = network.<statusCode>` (see [`AMENDMENT-pulse-type-parity.md`](AMENDMENT-pulse-type-parity.md)). Logs would lose hierarchy with navigation/session traces; metrics duplicate span duration without per-request context.

## Vendor comparison (discriminators)

| Product | Key | Web vs mobile |
|--------|-----|----------------|
| Sentry | `span.op` | Same `http.client` — shared queries |
| Datadog RUM | `resource.type` | Often split per platform |
| **Pulse** | `pulse.type` | **Single `otel_traces` table** — web must use `network.<code>` like Android so `LIKE 'network.%'` dashboards include browser traffic |

## Flush / export

Spans follow the **existing trace pipeline**: `BatchSpanProcessor` schedule (`scheduleDelayMillis`, shortened in ecommerce-demo E2E via `VITE_PULSE_BATCH_DELAY_MS`). **`pagehide`** (see `sdk.ts`) calls `WebTracerProvider.forceFlush()` alongside logs/metrics — no separate network flush hook required.

## Gate

**`PulseFeature.NETWORK_INSTRUMENTATION`** (`network_instrumentation`) applies to SDK **`pulse_web_js`** via remote config `features[]`; absent feature → treat as enabled (`FeatureGate`). Local override: `instrumentations.network.enabled`.

## OTel HTTP spans spec alignment

Stable semconv ([HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)): Pulse Web follows attribute names; intentional deviations (`error.type` class strings) and deferred histogram metric are recorded in [`PLAN-C-otel-spec-alignment.md`](PLAN-C-otel-spec-alignment.md) and [`ADR-network.md`](ADR-network.md).

## References

- OTel JS browser: `@opentelemetry/instrumentation-fetch`, `@opentelemetry/instrumentation-xml-http-request`.
- Stable HTTP semconv: `http.request.method`, `url.full`, `http.response.status_code`, `server.address`, etc.
- Canonical attribute table: [`../v1/02-instrumentations/network.md`](../v1/02-instrumentations/network.md).
- OTel HTTP spans spec: https://opentelemetry.io/docs/specs/semconv/http/http-spans/
- OTel HTTP metrics spec: https://opentelemetry.io/docs/specs/semconv/http/http-metrics/

---

## OTel spec findings (reviewed 2026-05-03)

### HTTP client span — required attributes vs Pulse Web

| Attribute | OTel level | Pulse status |
|-----------|-----------|-------------|
| `http.request.method` | Required | ✅ |
| `server.address` | Required | ✅ |
| `server.port` | Required | ⚠️ Pulse skips default ports (80/443); `URL.port = ""` for defaults. Verify base OTel lib covers it. |
| `url.full` | Required | ✅ — must not contain credentials (gap: `user:pass@` not stripped). |
| `error.type` | Cond. required on error | ⚠️ Spec expects `"404"` / `"500"`; Pulse uses `"4xx"` / `"5xx"` class strings — intentional deviation. |
| `http.response.status_code` | Cond. required | ✅ |
| `network.protocol.version` | Recommended | ❌ Not set. Available via `PerformanceResourceTiming.nextHopProtocol`. |
| `network.peer.address` | Recommended | ❌ Not available from browser Fetch/XHR API. Accepted gap. |

### `error.type` — intentional deviation from spec

OTel: set to HTTP status code as string (`"404"`) or exception class for transport errors.  
Pulse: `"4xx"` / `"5xx"` / `"network_error"` / `"cors_error"` — class groupings.  
Intentional: ClickHouse error-rate queries use class grouping across Android + Web.

### `http.client.request.duration` — OTel Stable Required metric

- Histogram, unit **seconds**, buckets `[0.005 … 10]`.
- Pulse Web does NOT emit this. `http.duration` span attribute (ms) is a per-span scalar — different instrument.
- Deferred: opt-in config flag `emitRequestDurationMetric` in `instrumentations.network`. See PLAN-C §P3.5.
