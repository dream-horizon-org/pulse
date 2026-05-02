# PLAN-C — OTel HTTP spec alignment

**Status:** PROPOSED  
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

**Decision needed:**
- **Option A — Keep Pulse convention:** `"4xx"` / `"5xx"` is intentional — it's a Pulse product
  signal, not a raw OTel attribute. Document as deliberate deviation in ADR.
- **Option B — Dual emit:** Emit both `error.type = "404"` (OTel-spec) and keep class string
  under a Pulse-specific key.
- **Recommendation: Option A.** The class grouping is what ClickHouse queries use. Spec
  deviation is acceptable as long as it is documented. Add one-liner to ADR and parity doc.

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

---

### 4. `server.port` — always required, not just non-standard

**OTel spec:** `server.port` is **Required**.

**Pulse Web:** Only set for non-standard ports (skips 80, 443, and empty string).

**Fix:** Remove the port-skip guard. Always set `server.port` if parseable from the URL.
Port 80 for `http:` and 443 for `https:` are valid values the spec expects.

```ts
// current (skips standard ports)
if (port !== "" && port !== "80" && port !== "443") { ... }

// correct (always set if present in URL)
// Note: URL.port is "" for default ports — so default-port requests won't have it.
// OTel base instrumentation may already cover this; verify before changing.
```

**Note:** `URL.port` is `""` for default ports (80/http, 443/https). So even after removing
the guard, default-port URLs produce no port value — this is unavoidable without hardcoding
the default. The OTel FetchInstrumentation may already emit `server.port` for explicit ports.
Verify against what the base library already sets before changing `applyPulseHttpClientSpanAttributes`.

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
| P1.1 | Document `error.type` convention as intentional deviation (ADR + parity doc) | **Now — docs only** | Trivial |
| P1.2 | Strip `url.username` + `url.password` in `sanitizeHttpUrl` | **Now — 2 lines** | Trivial |
| P2.3 | `network.protocol.version` from `PerformanceResourceTiming.nextHopProtocol` | **Now** | Small |
| P2.4 | `server.port` guard removal + verify base OTel lib behavior | **Now — verify first** | Small |
| P3.5 | `http.client.request.duration` metric + `emitRequestDurationMetric` flag in config | **Defer — own milestone** | Medium |

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
- `src/utils/network-http.ts` — verify base OTel library behavior, then remove guard if needed
- `src/__tests__/network-http.test.ts` — test explicit port URL sets `server.port`

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
