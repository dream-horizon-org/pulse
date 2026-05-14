# Web Vitals Instrumentation — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/instrumentations/web-vitals/SPEC.md`

---

## 1. Goal

Emit **Core Web Vitals** and related paint/timing signals as OTLP **log records** with `pulse.type = web_vital`, using the browser **`web-vitals`** library callbacks (`onLCP`, `onINP`, `onCLS`, `onFCP`, `onFID`, `onTTFB`). **Plan B:** log events (not OTel metrics histograms, not a separate dashboard-only UI channel).

---

## 2. Assumptions

- **Web-only — no Android/React Native parity:** Mobile SDKs do not emit browser CWV; this instrumentation exists only for `platform = web`.
- **FID deprecation:** Chrome pushes **INP** as the responsiveness metric; **FID** remains wired via `onFID` for older browsers / transitional reporting — treat INP as primary for interaction readiness.
- **`window` required:** `install()` returns immediately when `typeof window === "undefined"` (SSR/build).

---

## 3. Requirements

**R1 — OTLP logs:** Each metric emission uses `LoggerProvider` + body `web_vital` (semconv `LogBody.WEB_VITAL`).

**R2 — Metrics:** Register handlers for **LCP**, **INP**, **CLS**, **FCP**, **FID**, **TTFB** via `web-vitals` entrypoints.

**R3 — Attributes:** Every log includes `pulse.type`, `web_vital.name`, `web_vital.value`, `web_vital.rating`; optional `web_vital.navigation_type` when `Metric.navigationType` is set.

**R4 — Flush:** On `visibilitychange` → hidden and `pageshow` with `event.persisted` (BFCache restore), call `loggerProvider.forceFlush()` so Buffered batches exit before tab discard.

**R5 — Gating:** Subject to `InstrumentationRegistry` + `PulseFeature.WEB_VITALS` and local `instrumentations.webVitals.enabled`.

---

## 4. Architectural Design

### Why Plan B (log events) over Plan A (OTel metrics histogram)

**Plan A** (OTel metrics histogram): good for aggregation backends tuned for metrics pipelines; would duplicate schema work already normalized as logs in Pulse mobile/web alignment.

**Plan B** (chosen): OTLP **logs** mirror other Pulse semantic logs (`pulse.type`), reuse export filters and ClickHouse log pipelines, and carry Google `rating` buckets (`good` / `needs-improvement` / `poor`) verbatim.

**Plan C** (UI-only): rejected — no collector telemetry.

---

## 5. LLD

### 5.1 Signal type

| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| `pulse.type` | string | semconv | Yes | Always `web_vital` |
| `web_vital.name` | string | `Metric.name` | Yes | `LCP`, `INP`, `CLS`, `FCP`, `FID`, `TTFB`, … |
| `web_vital.value` | number | `Metric.value` | Yes | Unit depends on metric (ms, score, …) |
| `web_vital.rating` | string | `Metric.rating` | Yes | `good` \| `needs-improvement` \| `poor` |
| `web_vital.navigation_type` | string | `Metric.navigationType` | No | When library provides it |
| `session.id` | string | global attrs processor | Yes | Inherited on export |
| `screen.name` | string | global attrs processor | No | Inherited |
| `platform` | string | Resource `os.name` | Yes | `web` |

**`navigation_id`:** Not a dedicated attribute on this instrumentation today; navigation context may appear via **`web_vital.navigation_type`** when populated by `web-vitals`. Cross-route correlation uses **`session.id`** + **`screen.name`** + timestamps.

### 5.2 Metric coverage

| Metric | Role |
|---|---|
| **LCP** | Largest Contentful Paint |
| **INP** | Interaction to Next Paint (successor to FID for responsiveness) |
| **CLS** | Cumulative Layout Shift |
| **FCP** | First Contentful Paint |
| **FID** | First Input Delay (legacy; still subscribed for transitional dashboards) |
| **TTFB** | Time to First Byte |

### 5.3 React SPA behaviour

- Vitals run in the **browser** after hydration; React itself does not wrap the handlers — any SPA framework works as long as `window`/`document` exist.
- **Strict Mode** double-mount in dev may duplicate subscription lifecycle; instrumentation registers once per successful `install()` — rely on registry not calling `install` twice.

### 5.4 Next.js App Router behaviour

- **Soft navigations** (client-side transitions) cause `web-vitals` to report **new** metric rounds for the active route when the browser schedules fresh paints/interactions — no separate Pulse hook is required inside `web-vitals.ts`.
- **SSR:** server produces no vitals; first client paint drives LCP/FCP/TTFB for that navigation.

### 5.5 Next.js Pages Router behaviour

- Traditional route changes still run in one long-lived document — `web-vitals` continues across `routeChangeComplete`; BFCache `pageshow` flush covers restore scenarios.

---

## 6. Test Coverage

### `src/__tests__/web-vitals-instrumentation.test.ts`

- Registers `onLCP`, `onINP`, `onCLS`, `onFID`, `onFCP`, `onTTFB`.
- Emitted attributes include `pulse.type`, `web_vital.name`, `web_vital.value`, `web_vital.rating`.
- `visibilitychange` hidden → `forceFlush`; `pageshow` persisted → flush.
- Gate-off / uninstall removes listeners.

### Manual QA (absorbed from `examples/ecommerce-demo/MANUAL-WEB-VITALS-DEMO.md`)

- Enable **`pulse_web_js`** feature with `web_vitals` sample rate and mock config JSON path so logs are not blacklist-filtered.
- Optional env/query toggles: `VITE_PULSE_WEB_VITALS_ENABLED`, `?pulse_wv_enabled=` — merge into `PulseProvider` config for local opt-in/out.
- Use JSON OTLP format + collector when validating `/v1/logs` payloads during demo manual passes.

---

## 7. Known Bugs & Gaps

### P0 (data contract — none identified)

No confirmed **P0** incorrect vital values attributable to this instrumentation layer at synthesis time.

### Other gaps

- **FID vs INP product messaging:** dashboards should prefer INP where available.
- **Per-route CLS/INP reset on SPA navigations** requires `web-vitals` v5 `reportSoftNavs` (not available in v4.x). Current behavior: flush-on-navigate (via `Pulse.notifySoftNavigation()` wired into the React / Next router-tracking hooks) exports accumulated CLS/INP per route but values are cumulative from page load, not reset per route.

---

## 8. Redundancy & Cleanup Notes

Deleted after triple-eval:

| Path |
|---|
| `pulse-web-otel/web-sdk-plan/v2-web-vitals/` (full folder) |
| `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/web-vitals.md` |
| `pulse-web-otel/examples/ecommerce-demo/MANUAL-WEB-VITALS-DEMO.md` |

---

## 9. Open Questions

1. Should we drop `onFID` subscription once browser share is negligible?
2. Should `navigation_id` become a first-class attribute once backend schema supports it?
