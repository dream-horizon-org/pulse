# Plan A — Web Vitals via OTLP Metrics (Histogram)

**Approach:** Current plan. Use `MeterProvider` + histograms, same pipeline as other Pulse metrics.  
**Status:** Proposed — problems identified, solutions documented below.  
**Compare with:** [PLAN-B-logs-events.md](./PLAN-B-logs-events.md) (industry-standard alternative).

---

## Why this approach was chosen

Pulse Web SDK already ships a full `MeterProvider` with:
- `PeriodicExportingMetricReader`
- `SampledPushMetricExporter`
- `GlobalAttributeInjectingMetricExporter`
- `BeforeSendMetricExporter`
- Disk buffer (IndexedDB) on failure
- `pagehide` keepalive flush

The intent: web vitals plug into that pipeline for free — batching, sampling, before-send, and disk recovery all inherited.

---

## Event lifecycle (end to end)

This section traces one LCP measurement from browser to ClickHouse.

### Step 1 — Browser fires LCP candidate

```
User navigates to /products
Browser paints hero image at t=1800ms
```

The browser's `PerformanceObserver` watching `largest-contentful-paint` entries emits a candidate. The `web-vitals` library is tracking this internally — your SDK code has not been called yet.

### Step 2 — User first interacts (or pagehide)

```
User clicks "Add to Cart" at t=4200ms
→ LCP is now locked. web-vitals fires onLCP callback.
metric = { name: "LCP", value: 1800, rating: "good", id: "v4-abc-123", delta: 1800 }
```

`metric.value` = 1800ms (the locked-in LCP).  
`metric.id` = stable string, unique to this page load instance. Same across all callbacks for this page.  
`metric.rating` = `"good"` | `"needs-improvement"` | `"poor"` — from Google's thresholds (LCP: good < 2500ms).

### Step 3 — Instrumentation records into histogram

```ts
// Inside WebVitalsInstrumentation
onLCP((metric) => {
  lcpHistogram.record(metric.value, {
    [AttributeKey.PULSE_TYPE]: PulseType.WEB_VITAL,
    [AttributeKey.WEB_VITAL_NAME]: "LCP",
    [AttributeKey.WEB_VITAL_RATING]: metric.rating,
  });
  // Force flush immediately — do not wait for periodic export
  void meterProvider.forceFlush();
});
```

`histogram.record(1800, attrs)` → OTel SDK places 1800ms into the pre-configured bucket
(e.g. bucket `[1000ms–2500ms]` increments by 1). The raw value `1800` is gone — only the
bucket it fell into is remembered.

### Step 4 — forceFlush sends the export

`meterProvider.forceFlush()` triggers the `PeriodicExportingMetricReader` to immediately
export the current batch rather than waiting for the next scheduled interval (default 5s).

The OTLP payload sent to `/v1/metrics`:

```json
{
  "resourceMetrics": [{
    "resource": { "attributes": [
      { "key": "project.id", "value": "demo-project" },
      { "key": "platform", "value": "web" },
      { "key": "rum.sdk.name", "value": "pulse_web_js" }
    ]},
    "scopeMetrics": [{
      "scope": { "name": "pulse.web.web_vitals" },
      "metrics": [{
        "name": "pulse.web_vital.lcp",
        "unit": "ms",
        "histogram": {
          "dataPoints": [{
            "attributes": [
              { "key": "pulse.type",       "value": "web_vital" },
              { "key": "web_vital.name",   "value": "LCP" },
              { "key": "web_vital.rating", "value": "good" },
              { "key": "session.id",       "value": "sess-xyz" },
              { "key": "screen.name",      "value": "/products" }
            ],
            "count": 1,
            "sum": 1800,
            "bucketCounts": [0, 0, 0, 1, 0, 0],
            "explicitBounds": [500, 1000, 2500, 4000, 8000]
          }]
        }
      }]
    }]
  }]
}
```

### Step 5 — OTLP Collector → ClickHouse

Collector receives the OTLP payload, writes to `otel.otel_metrics_histogram`:

```
MetricName         = "pulse.web_vital.lcp"
MetricUnit         = "ms"
Attributes         = {"pulse.type": "web_vital", "web_vital.name": "LCP", ...}
Count              = 1
Sum                = 1800
BucketCounts       = [0, 0, 0, 1, 0, 0]
ExplicitBounds     = [500, 1000, 2500, 4000, 8000]
ProjectId          = "demo-project"    ← materialized column
SessionId          = "sess-xyz"        ← materialized column
```

### Step 6 — Query (example p75 LCP)

```sql
SELECT quantileExact(0.75)(Sum / Count) AS p75_lcp_ms
FROM otel.otel_metrics_histogram
WHERE
    ProjectId   = 'your-project'
    AND TimeUnix >= now() - INTERVAL 7 DAY
    AND MetricName = 'pulse.web_vital.lcp'
    AND platform = 'web'  -- via Attributes map
```

> `Sum / Count` gives the mean value per data point. For single-observation data points
> (one record per page load) `Sum = the value` and `Count = 1`, so this equals the raw value.

---

## Instrument types per vital

| Vital | Unit | Instrument | Rationale |
|-------|------|-----------|-----------|
| LCP   | `ms` | Histogram | p75/p95 across users |
| INP   | `ms` | Histogram | p75/p95 across users |
| CLS   | `"1"` (unitless) | Histogram | Same — OTel unit `"1"` for dimensionless |
| FID   | `ms` | Histogram | Optional, legacy |

**CLS uses `unit: "1"` not `unit: "ms"`.** Do not confuse with timing vitals at query time.

---

## Problems identified and solutions

### Problem 1 — Flush ordering (RESOLVED)

**The bug:** Both `web-vitals` callbacks and the SDK `pagehide` flush are registered as
`pagehide` listeners. If SDK flush fires first, `histogram.record()` happens after the
export — the value is never sent.

**Solution:** Call `meterProvider.forceFlush()` explicitly inside each callback,
immediately after `histogram.record()`. Do not rely on listener order. The `forceFlush()`
call is fire-and-forget (`void`) — the keepalive fetch completes even after page unload.

```ts
onLCP((metric) => {
  lcpHistogram.record(metric.value, buildAttrs(metric));
  void meterProvider.forceFlush(); // ← explicit, not order-dependent
});
```

### Problem 2 — `reportAllChanges` corrupts percentiles (RESOLVED by decision)

**The bug:** With `reportAllChanges: true`, intermediate LCP candidates (1100ms, 2400ms final)
both land in the histogram. p75 is computed over intermediate + final values mixed. Result
is wrong — it underestimates true LCP.

**Solution:** Use `reportAllChanges: false` (library default). One observation per page load.
Final value only. p75 is correct.

**Trade-off accepted:** Crashes = lost data (see Problem 3).

### Problem 3 — Browser crash / force kill (UNRESOLVED — accepted gap for MVP)

**The bug:** `pagehide` never fires on crash or process kill. `forceFlush()` never called.
The final values are never exported. IndexedDB only saves data from **failed exports** —
if the export was never triggered, nothing is written to IndexedDB.

**For MVP:** Accept this gap. Crashes are < 5% of sessions in practice.  
**Future path:** See [PLAN-B-logs-events.md](./PLAN-B-logs-events.md) which solves this
via `reportAllChanges: true` + `metric.id` deduplication on logs.

### Problem 4 — CLS `reportAllChanges` semantics (RESOLVED by decision)

`web-vitals` v3+ reports the largest "session window" score for CLS. Default reports once
on pagehide. This is the correct final value to record into the histogram.

Note: "session window" here is a **browser measurement concept** — sequences of layout
shifts within 1 second of each other, max 5 seconds. It is **not** the same as a user
session. CLS = score of the largest such window during the page lifetime.

### Problem 5 — Future deduplication if `reportAllChanges` ever enabled (SCHEMA CHANGE REQUIRED)

If at any point the team wants crash recovery by enabling `reportAllChanges: true`, the
current `MergeTree` table cannot deduplicate intermediate values. The fix requires:

1. Add `MetricId` materialized column: `Attributes['web_vital.metric_id']`
2. Change engine to `ReplacingMergeTree(TimeUnix)` with `MetricId` in ORDER BY
3. All queries use `FINAL` modifier

**This is a significant schema migration.** Do not enable `reportAllChanges: true`
without completing this first.

---

## SDK implementation sketch

```ts
// src/instrumentations/web-vitals.ts
import { metrics } from "@opentelemetry/api";
import { onLCP, onINP, onCLS, onFID } from "web-vitals";
import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";

export class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = "web-vitals";
  private meterProvider?: ReturnType<SdkContext["meterProvider"]>;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    // NOTE: meterProvider must be set before install() — guaranteed by installAll()
    // running after metrics.setGlobalMeterProvider in finishStart().
    const meter = metrics.getMeter("pulse.web.web_vitals");
    const mp = sdk.meterProvider;

    const record = (name: string, unit: string, value: number, rating: string) => {
      const hist = meter.createHistogram(`pulse.web_vital.${name.toLowerCase()}`, { unit });
      hist.record(value, {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]: PulseWebSemconv.PulseType.WEB_VITAL,
        "web_vital.name": name,
        "web_vital.rating": rating,
      });
      // Solve flush-ordering problem — force export immediately
      void mp.forceFlush();
    };

    // reportAllChanges: false (default) — one value per page load, final only
    onLCP((m) => record("LCP", "ms", m.value, m.rating));
    onINP((m) => record("INP", "ms", m.value, m.rating));
    onCLS((m) => record("CLS", "1",  m.value, m.rating));
    // onFID((m) => record("FID", "ms", m.value, m.rating)); // optional legacy
  }

  uninstall(): void {
    // web-vitals v4 does not expose a cancel() API per observer.
    // Listeners remain but the instrumentation reference is dropped.
    // SDK shutdown ensures meterProvider flush + no further exports.
  }
}
```

---

## Registry wiring

```ts
// instrumentation-registry.ts — inside installAll()
if (this.shouldInstall(InstrumentationKeys.WEB_VITALS)) {
  const wvInstr = new WebVitalsInstrumentation();
  wvInstr.install(this.sdk);
  this.installed.push(wvInstr);
}
```

Pattern: same as session block. No `registerAndInstall` with key — avoids double gate check.

---

## Backend change required

Add to `Features.java`:
```java
web_vitals
```

Add to `DefaultSdkConfigTemplate.java`:
```java
features.add(createFeature(Features.web_vitals, 1.0, webJsSdk));
```

Update `DefaultSdkConfigTemplateTest` — expected feature count increases by 1.

---

## Open questions (must resolve before PR)

| # | Question | Recommended answer |
|---|----------|--------------------|
| 1 | Final metric name prefix? | `pulse.web_vital.*` — document in semconv |
| 2 | Ship FID in MVP? | No — deprecated CWV; add as opt-in later |
| 3 | Histogram bucket boundaries for LCP/INP? | `[100, 200, 500, 800, 1000, 1800, 2500, 4000, 8000]` ms (Google's rating thresholds as natural boundaries) |
| 4 | `uninstall()` gap — web-vitals has no cancel API | Document as known; listeners are no-ops after SDK shutdown since `forceFlush` on a shut-down provider is a no-op |

---

## Summary — what this approach gives you and what it doesn't

| | Plan A |
|--|--------|
| Reuses existing metrics pipeline | ✓ |
| Correct p75/p95 per session | ✓ (with `reportAllChanges: false`) |
| Crash recovery | ✗ |
| No schema change | ✓ (for MVP) |
| Future deduplication | Requires `ReplacingMergeTree` migration |
| Industry standard for RUM | ✗ (industry uses events/logs) |
