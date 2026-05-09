# PLAN-D — `http.client.request.duration` metric

**Status:** PROPOSED  
**Extends:** PLAN-C §P3.5 (`emitRequestDurationMetric` config key already reserved)  
**OTel spec:** https://opentelemetry.io/docs/specs/semconv/http/http-metrics/  
**Android parity:** `OkHttpInstrumentation.setEmitExperimentalHttpClientTelemetry(true)`

---

## What and why

The OTel HTTP metrics spec defines `http.client.request.duration` as a **Stable, Required**
histogram (unit: seconds). Pulse Web currently has no histogram for network latency — only the
per-span `http.duration` scalar attribute (ms, in `otel_traces`).

These are different instruments:

| | `http.duration` span attr | `http.client.request.duration` metric |
|---|---|---|
| Storage | `otel_traces` `SpanAttributes` | `otel_metrics_histogram` |
| Unit | ms | **seconds** |
| Cardinality | One row per request | Aggregated over batch window |
| Query use | Per-request drill-down | p50/p95/p99 by `server.address` + method |
| OTel spec | Pulse custom | Stable Required |

Pulse dashboards need the histogram to show latency percentiles without scanning spans.

---

## Gate

`instrumentations.network.emitRequestDurationMetric?: boolean` — already in `config.ts`.  
Default `false` — opt-in, mirrors Android's `setEmitExperimentalHttpClientTelemetry(true)`.  
No remote feature gate needed (config-only, not a separate `PulseFeature` enum value).

---

## Key decisions

### D1 — Meter access: global OTel API vs `SdkContext.meterProvider`

**Context:** `sdk.ts` calls `metrics.setGlobalMeterProvider(meterProvider)` at line 323, then
`installAll()` at line 339. Global is set before `install()` runs.

| Option | Approach | Tradeoff |
|--------|----------|----------|
| **A (chosen)** | `metrics.getMeter("pulse-web")` from OTel global API in `install()` | No `SdkContext` change; works because global is set first; consistent with how other OTel-native instrumentations get meters |
| B | Add `meterProvider?: MeterProvider` to `SdkContext`; expose getter on `PulseWebSDK` | Explicit contract, testable in isolation; more boilerplate |

**Decision: Option A.** No `SdkContext` change. The global is guaranteed before `install()`. If
the global pattern ever becomes a problem (e.g. SSR, multi-SDK), `SdkContext` can be extended
then.

---

### D2 — Duration source

`applyPulseHttpClientSpanAttributes` already calls `resourceTimingDurationMs(perfKey)` which
returns ms from `PerformanceResourceTiming.duration`. Convert to seconds for the histogram.

When `PerformanceResourceTiming` is absent (CORS-opaque, cross-origin without Timing-Allow-Origin
header, or Playwright stub): **skip recording**. Do not fall back to wall-clock or span duration
— those are less accurate and may include SDK overhead.

---

### D3 — Bucket configuration

Hardcode OTel spec recommended boundaries:
`[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10]` (seconds).

Not configurable in the first pass — same approach as web-vitals histograms. Add
`histogramBoundaries?: number[]` to the config subtree only if a product need arises.

---

## Attribute contract

Per OTel HTTP metrics spec — attributes on the histogram observation:

| Attribute | Required? | Source |
|-----------|-----------|--------|
| `http.request.method` | Required | `params.method.toUpperCase()` |
| `server.address` | Required | `parsed.hostname` |
| `server.port` | Required | already computed in `applyPulseHttpClientSpanAttributes` |
| `http.response.status_code` | Recommended | `params.statusCode` |
| `error.type` | Recommended (on error) | same class string Pulse uses on span |
| `network.protocol.version` | Recommended | `resourceTimingProtocolVersion(perfKey)` |
| `url.scheme` | Recommended | `parsed.protocol.replace(":", "")` |

**No `url.full` on the metric** — that's high cardinality; spec does not include it on metrics.

---

## Lifecycle

```
NetworkInstrumentation.install()
  if emitRequestDurationMetric !== true → skip histogram creation
  meter = metrics.getMeter("pulse-web")   ← global already set by sdk.ts
  this._durationHistogram = meter.createHistogram(
    "http.client.request.duration",
    { unit: "s", description: "...", advice: { explicitBucketBoundaries: [...] } }
  )

applyCustomAttributesOnSpan callback (Fetch + XHR)
  [ existing applyPulseHttpClientSpanAttributes call ]
  if this._durationHistogram && durationMs !== undefined
    this._durationHistogram.record(durationMs / 1000, metricAttrs)

NetworkInstrumentation.uninstall()
  this.fetchInstr?.disable()
  this.xhrInstr?.disable()
  this._durationHistogram = undefined   ← clear ref; MeterProvider lifecycle handles cleanup
```

`pagehide` force-flush: `meterProvider.forceFlush()` already called in `sdk.ts:378` — no new
flush wiring needed.

---

## Touchpoints

| File | Change |
|------|--------|
| `src/instrumentations/network.ts` | Private `_durationHistogram?: Histogram` field; create in `install()` when flag on; record in both Fetch + XHR callbacks; clear in `uninstall()` |
| `src/utils/network-http.ts` | No change — duration + protocol version already returned; reuse in `network.ts` |
| `src/semconv.ts` | No new keys needed — histogram attr names are standard OTel strings |
| `src/types/config.ts` | Already done — `emitRequestDurationMetric?: boolean` reserved |
| `src/__tests__/network.test.ts` | New test file (or extend existing `network-http.test.ts`): mock `metrics.getMeter`; verify `histogram.record` called with correct seconds value and attributes |
| `examples/ecommerce-demo/src/App.tsx` | Add `emitRequestDurationMetric: true` to SDK init when `?pulse_network_metric=1` (same pattern as `?pulse_network_enabled=0`) — demo must pass the flag for E2E to exercise the metric path |
| `examples/ecommerce-demo/e2e/m4-network.spec.ts` | New test **M1:** enable flag, fetch probe URL, `otlp.waitForMetric("http.client.request.duration")`, assert `asDouble` finite and `>= 0`, assert `http.request.method` on data point |
| `examples/ecommerce-demo/e2e/fixture.ts` | `findAllMetricPoints` + `waitForMetric` already present — no changes needed |

**No backend changes.** Metric goes into existing `otel_metrics_histogram` table via the already-wired OTLP metrics exporter.

---

## Unit matrix (Vitest)

| Case | Assert |
|------|--------|
| `emitRequestDurationMetric: false` (default) | `histogram.record` never called |
| `emitRequestDurationMetric: true` + duration present | `histogram.record(durationMs/1000, attrs)` called once |
| Duration absent (`resourceTimingDurationMs` returns `undefined`) | `histogram.record` not called |
| Attributes on record call | `http.request.method`, `server.address`, `server.port`, `http.response.status_code`, `url.scheme` present |
| Error request (`status=404`) | `error.type = "4xx"` on metric attrs |
| `uninstall()` | `_durationHistogram` cleared |

---

## E2E outline (Playwright)

| ID | Scenario | Spec |
|----|----------|------|
| M1 | `emitRequestDurationMetric: true` via `?pulse_network_metric=1`; fetch probe → `otlp.waitForMetric("http.client.request.duration")`; assert `asDouble` finite and `>= 0`; assert `http.request.method = "GET"` on data point | `m4-network` new test |
| M2 (negative) | Default (`emitRequestDurationMetric` absent / false); fetch probe; `waitForTimeout`; assert `findAllMetricPoints(captured, "http.client.request.duration").length === 0` | `m4-network` new test |

**Note on timing availability:** Playwright `page.route` fulfillment may not produce a
`PerformanceResourceTiming` entry, so `http.duration` and `_durationHistogram.record` may both
be skipped even when the flag is on. If M1 is consistently empty, use a real in-process server
response instead of `page.route` — or assert the metric only when `asDouble` is present
(`waitForMetric` with a longer timeout; skip in CI if flaky).

---

## Explicit deferrals

| Item | Rationale |
|------|-----------|
| `histogramBoundaries` config | No product request; add only if teams need custom bucket resolution |
| Metric-level gate via `PulseFeature` | `emitRequestDurationMetric` config flag is sufficient; no remote gate needed until cross-team rollout |
| `http.client.request.body.size` / `response.body.size` histograms | OTel Development stability — defer until stable |
| XHR `url.scheme` | `xhr.responseURL` is always absolute so scheme is parseable; implement same as Fetch |

---

## Open question before coding

**Can `PerformanceResourceTiming` be reliably produced in Playwright E2E for the M1 positive
test?** If `page.route` suppresses timing entries, M1 needs either a `page.route` that calls
`route.continue()` (letting the request hit the real dev server) or a local Express server
started in the Playwright config. Resolve this in Demo readiness (D0a) before writing the
E2E spec.
