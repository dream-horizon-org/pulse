# PLAN-C — OTel HTTP spec alignment

**Status:** P1–P2 **IMPLEMENTED** (2026-05-03); P3 metric **deferred** (`emitRequestDurationMetric` reserved on config only).  
**Source:** OTel HTTP spans spec + metrics spec reviewed 2026-05-03  
**Refs:** https://opentelemetry.io/docs/specs/semconv/http/http-spans/  
         https://opentelemetry.io/docs/specs/semconv/http/http-metrics/

---

## What this plan covers

Gaps found when comparing Pulse Web network instrumentation against the stable OTel HTTP
semantic conventions. Grouped into three tiers:

- **P1 — Spec violation** — directly contradicts the stable spec  
- **P2 — Recommended missing** — OTel says Recommended; browser-feasible  
- **P3 — Future / opt-in** — OTel Stable metric not yet wired; needs config flag

---

## P1 — Spec violations

### 1. `error.type` value convention

**OTel spec:** `error.type` must be the HTTP status code as a string (e.g. `"404"`, `"500"`) or
an exception class name for transport failures.

**Pulse Web:** Uses class strings — `"4xx"`, `"5xx"`, `"network_error"`, `"cors_error"`.

**Decision:** **Option A** — keep Pulse class strings (`4xx` / `5xx` / …). Documented in [`ADR-network.md`](ADR-network.md) and [`04-contract-parity.md`](04-contract-parity.md).

---

### 2. `url.full` — credentials not stripped

**OTel spec:** "`url.full` MUST NOT contain credentials (user:password@…)."

**Pulse Web (`sanitizeHttpUrl`):** Strips query params only. `URL.username` / `URL.password`
are not cleared before setting the attribute.

**Fix (one-liner in `sanitizeHttpUrl`):**
```ts
u.username = "";
u.password = "";
```

Add after `u.search = ""`. Practically rare in modern browser Fetch (Chrome blocks credentials
in cross-origin), but required for spec compliance.

---

## P2 — Recommended, browser-feasible

### 3. `network.protocol.version` (Recommended)

Available via `PerformanceResourceTiming.nextHopProtocol`:
- `"http/1.1"` → `"1.1"`
- `"h2"` → `"2"`
- `"h3"` → `"3"`

**Where to add:** `resourceTimingDurationMs` already looks up `PerformanceResourceTiming` by
URL. Extract a second helper `resourceTimingProtocolVersion(url): string | undefined` using
the same `getEntriesByName` lookup.

**Attribute key:** `PulseWebSemconv.AttributeKey.NETWORK_PROTOCOL_VERSION = "network.protocol.version"`

**Emit rule:** Set only when present; omit when absent (opt-in timing entry, CORS-blocked
resources may not expose it).

**E2E:** Do not assert in `m4-network` when using Playwright `page.route` — synthetic responses usually lack a real `PerformanceResourceTiming` with `nextHopProtocol`. Same deferral as PLAN-B (`network.protocol.version` row).

---

### 4. `server.port` — always required, not just non-standard

**OTel spec:** `server.port` is **Required**.

**Implementation (shipped):** When `URL.port` is empty, Pulse sets **443** for `https:` and **80** for `http:`; otherwise uses the explicit numeric port from the URL. This satisfies the spec without relying on upstream FetchInstrumentation alone.

```ts
let serverPort: number | undefined;
if (parsed.port !== "") {
  serverPort = Number(parsed.port);
} else if (parsed.protocol === "https:") {
  serverPort = 443;
} else if (parsed.protocol === "http:") {
  serverPort = 80;
}
```

**E2E:** `m4-network` P1 asserts `server.port` is a finite number (exact port follows demo origin). Other rows defer exact-value checks to Vitest.

---

## P3 — Future / opt-in metric

### 5. `http.client.request.duration` — OTel Stable Required metric

**OTel spec:** Stable, Required, Histogram, unit = `s` (seconds).

**Pulse Web:** Not emitted. Pulse has `http.duration` as a **span attribute** in ms (convenience
scalar, not a metric instrument).

**These are different things:**
- `http.duration` (span attr) = per-request value queryable from `otel_traces` via ClickHouse
- `http.client.request.duration` (metric) = aggregated histogram in `otel_metrics_histogram`,
  queryable for p50/p95/p99 by `server.address` + `http.request.method` without scanning spans

**Config flag — mirrors Android:**

Android's `OkHttp3Instrumentation` uses `setEmitExperimentalHttpClientTelemetry(true)`.
Web should add an equivalent opt-in to `InstrumentationConfig.network`:

```ts
network?: {
  // ... existing fields ...

  /**
   * Emit OTel `http.client.request.duration` histogram metric (unit: seconds, stable semconv).
   * Default false — opt-in because it doubles OTLP metric payload for every request.
   * Mirrors Android `OkHttp3Instrumentation.setEmitExperimentalHttpClientTelemetry(true)`.
   */
  emitRequestDurationMetric?: boolean;
}
```

**Implementation path (when this is built):**
1. Require a `MeterProvider` in `SdkContext` (already needed for web-vitals metrics — check if
   already wired).
2. In `NetworkInstrumentation.install`, create a `Histogram` instrument:
   `meter.createHistogram("http.client.request.duration", { unit: "s" })`.
3. In `applyCustomAttributesOnSpan`, record the duration: `histogram.record(durationS, { "http.request.method": method, "server.address": host, "server.port": port, "http.response.status_code": status, "error.type": errorType, "url.scheme": scheme })`.
4. Duration source: `PerformanceResourceTiming.duration / 1000` (already computed for span attr).
5. Guard: only record when `net.emitRequestDurationMetric === true`.
6. Backend: no schema change needed — goes into existing `otel_metrics_histogram` table.

**Recommended bucket boundaries (from spec):**
`[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10]` seconds.

---

## Summary — what to implement now vs defer

| # | Item | Now or defer | Effort |
|---|------|-------------|--------|
| P1.1 | Document `error.type` convention as intentional deviation (ADR + parity doc) | **Done** | Trivial |
| P1.2 | Strip `url.username` + `url.password` in `sanitizeHttpUrl` | **Done** | Trivial |
| P2.3 | `network.protocol.version` from `PerformanceResourceTiming.nextHopProtocol` | **Done** | Small |
| P2.4 | `server.port` for default http/https when `URL.port` empty | **Done** | Small |
| P3.5 | `http.client.request.duration` metric + `emitRequestDurationMetric` | **Defer** — config key reserved; histogram not wired | Medium |

---

## Files that change

### P1.2 (`url.full` credentials)
- `src/utils/network-http.ts` — `sanitizeHttpUrl`: add `u.username = ""; u.password = "";`
- `src/__tests__/network-http.test.ts` — add test: URL with `user:pass@` → credentials stripped

### P2.3 (`network.protocol.version`)
- `src/semconv.ts` — add `NETWORK_PROTOCOL_VERSION: "network.protocol.version"` to `AttributeKey`
- `src/utils/network-http.ts` — add `resourceTimingProtocolVersion(url): string | undefined`
- `src/utils/network-http.ts` — `applyPulseHttpClientSpanAttributes`: call helper, `setOptionalString`
- `src/__tests__/network-http.test.ts` — mock `performance.getEntriesByName`; assert value set

### P2.4 (`server.port` always-required)
- `src/utils/network-http.ts` — explicit **443** / **80** fallbacks when `URL.port` is empty
- `src/__tests__/network-http.test.ts` — https default + explicit `:8080`
- `examples/ecommerce-demo/e2e/m4-network.spec.ts` — P1 asserts finite `server.port`

### P3.5 (`http.client.request.duration` metric — deferred)
- `src/types/config.ts` — add `emitRequestDurationMetric?: boolean` to `network` config
- `src/instrumentations/network.ts` — histogram instrument + record in span callback
- `src/__tests__/` — unit test for metric recording
- Backend: no change

### Docs (all tiers)
- `ADR-network.md` — add "OTel spec deviations" section: `error.type` convention
- `04-contract-parity.md` — note `error.type` deviation; add `network.protocol.version` row
- `01-research-network-ecosystem-and-industry.md` — add OTel spec findings section
- `v1/02-instrumentations/network.md` — update attribute table: `pulse.type` row, add `network.protocol.version` row, note `error.type` deviation
