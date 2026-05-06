# Research: OpenTelemetry, Core Web Vitals, and industry capture patterns

**Status:** Planning artifact (Phase A).  
**Audience:** Pulse Web SDK + backend stakeholders.  
**Inputs for:** [03-touchpoints-matrix.md](./03-touchpoints-matrix.md), [ADR-web-vitals.md](./ADR-web-vitals.md).

---

## 1. What “Web Vitals” means here

Google’s **Core Web Vitals** (field / real-user metrics) are the primary standard for web UX signals:


| Metric  | Measures                                   | Notes                                                            |
| ------- | ------------------------------------------ | ---------------------------------------------------------------- |
| **LCP** | Largest Contentful Paint (load)            | Element-driven; often one value per navigation                   |
| **INP** | Interaction to Next Paint (responsiveness) | Replaced FID as CWV; considers multiple interactions             |
| **CLS** | Cumulative Layout Shift (visual stability) | Session-window model; `web-vitals` reports deltas and updates    |
| **FID** | First Input Delay                          | Deprecated for CWV (Mar 2024); still useful for older dashboards |


Other useful lab/field metrics (TTFB, FCP) may be recorded alongside but are not always treated as “Core” three.

---

## 2. OpenTelemetry alignment

### 2.1 Semantic conventions (events)

OpenTelemetry defines **browser-related** semantic conventions, including a `**browser.web_vital`**-style event model (see [semantic-conventions](https://github.com/open-telemetry/semantic-conventions) under `model/browser/`). The intent is to represent a web vital as a structured **event** with:

- Identity of the metric (`name`: e.g. lcp, inp, cls, fid)
- Measured `**value`** and often `**delta**` (CLS especially)
- Stable `**id**` for the underlying metric instance where applicable

This aligns well with how the `[web-vitals](https://github.com/GoogleChrome/web-vitals)` JavaScript library surfaces callbacks.

### 2.2 Signal types in OTLP


| Signal                          | Typical use for Web Vitals                              | Pros                                                                   | Cons for Pulse                                                         |
| ------------------------------- | ------------------------------------------------------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| **Metrics** (histogram / gauge) | One OTel instrument per vital name; record observations | Native aggregation, dashboards, SLO-style views                        | Need stable metric names + attributes; CLS “delta” semantics need care |
| **Logs** (events)               | Emit one log record per callback with body + attributes | Maps 1:1 to `browser.web_vital` event shape; easy `pulse.type` tagging | Higher volume; aggregation often downstream                            |
| **Traces**                      | Span per vital or per navigation                        | Correlation with route/HTTP child spans                                | Easy to conflate with user journeys; not ideal as primary CWV store    |


**Recommendation for Pulse (preview for ADR):**

- Prefer **OTLP metrics** as the **primary** transport for numeric vitals (LCP, INP, CLS, optional FCP/TTFB), using instruments that accept the values reported by `web-vitals` (milliseconds for paint/input timing; CLS as unitless score).
- Optionally add **log events** only if product requires byte-for-byte parity with OTel `browser.web_vital` event docs or for debugging—avoid duplicating the same numeric series in both metrics and logs unless gated for dev.

Either way, `**pulse.type`** (and/or metric namespace) must identify the signal as a web vital for ClickHouse filtering consistent with other Pulse RUM data.

### 2.3 End-to-end flow (Pulse stack)

```text
Browser (web-vitals / PerformanceObserver)
  → Pulse Web SDK (instrumentation + MeterProvider / optional Logger)
  → OTLP/HTTP JSON or protobuf (`/v1/metrics`, optionally `/v1/logs`)
  → OTEL Collector (deploy config)
  → ClickHouse `otel` DB (`otel_metrics_*` unified view `otel_metrics`, and/or `otel_logs`)
```

Downstream:

- Queries filter by `**ProjectId**`, time range, and `**PulseType**` / metric name (same constraints as other OTLP signals).
- Materialized columns and maps follow existing ingestion patterns (resource attrs for `project.id`, `rum.sdk.name`, `platform=web`, etc.).

---

## 3. How other tools capture Web Vitals

### 3.1 `web-vitals` (Google)

- Subscribes to the right **PerformanceObserver** entry types and normalizes browser differences.
- Invokes a callback per metric update with `{ name, value, delta, id, rating, navigationType, ... }`.
- **Pulse already lists** `web-vitals` as a dependency in `pulse-web-otel/package.json`—implementation should wrap these callbacks, not reimplement observers.

### 3.2 RUM vendors (Datadog, New Relic, etc.)

- Generally hook the same browser APIs or `web-vitals`, batch/beacon on `**visibilitychange` / `pagehide`**, and map to their proprietary models.
- Different vendors choose metrics-only vs events; Pulse should stay **OTLP-native** and **semconv-friendly**.

### 3.3 Session stitching

- Vitals are tied to **URL / route**, **session**, and often **navigation type** (soft nav vs full load).
- Pulse should stamp `**session.id`**, `**screen.name**` or URL attrs, and navigation context on the same global attribute path as existing web instrumentation (`PulseWebSemconv`, global attrs processor).

---

## 4. Recommendations summary (input to Phase C/D)

1. **Primary signal:** OTLP **metrics** from the SDK’s existing `**MeterProvider`** (`createProviders` in `src/exporters.ts`), not a parallel custom beacon.
2. **Naming:** Align metric names and attributes with OTel browser semantics where possible; add `**pulse.type = web_vital`** (or per-vital subtype—decided in ADR) for Pulse-wide consistency.
3. **Do not** rely on UI-only mock types (`coreWebVitals` in session replay mocks) for ingestion truth—the SDK path is OTLP.
4. **Open questions** for ADR: exact histogram vs gauge per metric; whether INP needs histogram for percentiles in-backend vs SDK-side; duplicate suppression when remote config disables feature mid-session.

---

## 5. References

- [OpenTelemetry semantic conventions – browser](https://github.com/open-telemetry/semantic-conventions/tree/main/model/browser)
- [web-vitals library](https://github.com/GoogleChrome/web-vitals)
- [Web Vitals](https://web.dev/vitals/) (Google)
- Pulse exporters: `[pulse-web-otel/src/exporters.ts](../../src/exporters.ts)`

