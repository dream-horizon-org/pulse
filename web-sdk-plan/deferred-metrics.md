---
name: Web SDK — Metrics Gap Analysis
description: Metrics present in Android/iOS SDKs not yet planned for web — to be discussed and prioritised
type: project
---

## Context

Android and iOS SDKs capture metrics in three ways:

1. **Platform system metrics** — iOS MetricKit (CPU, memory, GPU, app launch, hang time, network transfer, exits); Android Choreographer (slow/frozen frame counts per interaction)
2. **Custom metric recording API** — iOS exposes `Pulse.trackLongCounterMetric()`, `trackDoubleGaugeMetric()`, `trackLongHistogramMetric()` etc. for arbitrary business metrics
3. **Derived metrics via SDK Config** — `PulseSamplingSignalProcessors` derives counters/gauges/histograms from span/log signal conditions, purely server-side, no SDK release needed

---

## What Doesn't Exist in the Browser (Expected Gap)

These iOS MetricKit metrics have **no browser API equivalent** and are not capturable:

| iOS Metric | Reason Not Available |
|---|---|
| CPU time | No browser API |
| GPU time | No browser API |
| Disk I/O | No browser API |
| Battery / Location activity | Not exposed to SDKs |
| App exit reasons (watchdog, OOM) | No browser equivalent |

This gap is **by design** — the browser sandbox intentionally limits low-level system access.

---

## What Has a Web Equivalent (Already Covered Under Different Names)

| Mobile Metric | Web Equivalent | Where Planned |
|---|---|---|
| MetricKit app launch timings | Navigation Timing API → `page.load_time`, `ttfb`, `tti` | 02.5 (as spans) |
| MetricKit hang time / ANR | Long Tasks > 50ms | 02.6 |
| MetricKit network transfer bytes | Resource Timing `transferSize` | 02.7 |
| Android slow/frozen frames | Long Tasks | 02.6 |
| MetricKit scroll hitch ratio | INP (Web Vitals) | 02.4 |

These are already in the plan. No action needed.

---

## Genuine Gaps to Discuss

### Gap 1 — Custom Metric Recording API (Missing)

The iOS SDK exposes a public API for customers to record arbitrary business metrics.
The web plan has no equivalent. Customers who instrument both mobile and web today expect the same API.

**Proposed API surface:**
```typescript
PulseWeb.trackMetric('orders.placed', 1, { type: 'counter' })
PulseWeb.trackMetric('cart.value', 1299.50, { type: 'gauge' })
PulseWeb.trackMetric('search.latency_ms', 320, { type: 'histogram' })
// CDN
pulse('trackMetric', 'orders.placed', 1, { type: 'counter' })
```
Backed by OTel `Meter.createCounter/createGauge/createHistogram`. Low complexity, high parity value.

**Questions to resolve:**
- Do we include this in v1 or post-v1?
- Should it support only counter + gauge (simple) or full histogram support as well?

---

### Gap 2 — Web Vitals Output as OTEL Metrics (Needs Confirmation)

02.4 plans Web Vitals (LCP, CLS, INP, FCP, TTFB) under signal kind "Metric" but this needs to be explicitly confirmed as proper **OTEL gauge observations** flowing into `otel_metrics_gauge` in ClickHouse — the same table iOS MetricKit data goes to. If they land as spans or logs instead, cross-platform metric queries and the Web Vitals dashboard won't work correctly.

**Action:** Confirm output kind in 02.4 before implementation starts.

---

### Gap 3 — Memory Gauge (Optional, Chrome-Only)

`performance.memory.usedJSHeapSize` / `jsHeapSizeLimit` — the closest web equivalent to MetricKit's `peak_memory_usage`.

**Constraints:**
- Chrome/Chromium only (Firefox and Safari do not expose this)
- Requires COOP + COEP response headers (`Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Embedder-Policy: require-corp`)
- Not all customer deployments will have these headers set

**Proposed handling:** Off by default. Emitted as a sampled gauge every 30s when available, gated by a feature flag in SDK Config. Gracefully no-ops on Firefox/Safari.

**Question:** Worth the complexity given Chrome-only + header requirement?

---

### Gap 4 — Derived Metrics via SDK Config (Missing)

The `PulseSamplingSignalProcessors` system (configure server-side rules to derive a counter/gauge/histogram whenever a span/log matches a condition) is not in the web plan. Without it, the web SDK won't support the same server-driven metric derivation that mobile supports.

**Example use case:** "Increment a counter every time a `device.crash` log is seen" — configured from the Pulse UI, no SDK release.

**Question:** Is this a v1 requirement or can it be added post-v1 once the signal pipeline is stable?

---

## Recommended Priority for Discussion

| Gap | Complexity | Value | Suggested Call |
|---|---|---|---|
| Web Vitals as OTEL metrics (Gap 2) | Low — confirm output format | High — needed for Web Vitals dashboard | Resolve before 02.4 implementation |
| Custom metric API (Gap 1) | Low–Medium | High — cross-platform parity | Decide v1 vs post-v1 |
| Derived metrics / SDK Config (Gap 4) | Medium | Medium | Post-v1 candidate |
| Memory gauge (Gap 3) | Low | Low–Medium | Post-v1, opt-in only |
