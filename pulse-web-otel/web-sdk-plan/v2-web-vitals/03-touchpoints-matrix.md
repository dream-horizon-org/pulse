# Touchpoints matrix: Web Vitals across Pulse

**Status:** Planning artifact (Phase C).  
**Prerequisites:** [01-research-otel-ecosystem-and-industry.md](./01-research-otel-ecosystem-and-industry.md), [02-research-otel-js-browser-and-pulse-sdk.md](./02-research-otel-js-browser-and-pulse-sdk.md).  
**Inputs for:** [ADR-web-vitals.md](./ADR-web-vitals.md).

This table lists where Web Vitals must be introduced or aligned. **MVP** = required for first shippable SDK + ingest; **Later** = product follow-up.

---

## 1. Web SDK (`pulse-web-otel/`)

| Touchpoint | File / area | Today | Action |
|------------|-------------|--------|--------|
| Semconv | [`src/semconv.ts`](../../src/semconv.ts) | `PulseType` has no `web_vital` | Add constant(s) and any metric attribute keys (e.g. `web_vital.name`, `web_vital.rating`) per ADR. |
| Instrumentation | New `src/instrumentations/web-vitals.ts` (or similar) | Missing | Implement `PulseInstrumentation`; register in registry. |
| Registry | [`src/instrumentation-registry.ts`](../../src/instrumentation-registry.ts) | Maps `WEB_VITALS` → feature; not installed | Call `registerAndInstall(..., InstrumentationKeys.WEB_VITALS)` in `installAll()`. |
| Config | [`src/types/config.ts`](../../src/types/config.ts) | `webVitals?: { enabled }` | Optional: extend with `reportOnly?: ('LCP' \| 'INP' \| ... )[]` if product needs subset (Later). |
| Remote types | [`src/types/remote-config.ts`](../../src/types/remote-config.ts) | `web_vitals` in `PulseFeatureName` | **No change** if names stay aligned with backend. |
| Feature gate | [`src/feature-gate.ts`](../../src/feature-gate.ts) | Works by feature name | Ensure backend sends `web_vitals` for `pulse_web_js` when product wants remote control. |
| Exporters | [`src/exporters.ts`](../../src/exporters.ts) | Metrics pipeline ready | Use existing `MeterProvider` only (ADR). |
| Before-send | [`src/before-send.ts`](../../src/before-send.ts), metric exporters | `beforeSendMetric` supported | Tests for dropping/redacting web vital metrics. |
| Public API | [`src/sdk.ts`](../../src/sdk.ts), `index` exports | No vitals-specific API | MVP: none required (`web-vitals` internal). Later: optional manual APIs if needed. |
| Demo | [`examples/ecommerce-demo/`](../../examples/ecommerce-demo/) | No vitals assertions | E2E coverage per test plan. |

**MVP:** semconv + instrumentation + registry + tests + demo hooks.

---

## 2. Backend (`backend/server/`) — SDK remote config

| Touchpoint | File / area | Today | Action |
|------------|-------------|--------|--------|
| Feature enum | [`service/configs/models/Features.java`](../../../backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/models/Features.java) | **No `web_vitals`** — gap vs web SDK string `web_vitals` | **Add** enum value `web_vitals` so persisted configs and JSON remain consistent with [`PulseFeature.WEB_VITALS`](../../src/types/remote-config.ts). |
| Default template | [`DefaultSdkConfigTemplate.java`](../../../backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/DefaultSdkConfigTemplate.java) | No row for web vitals | **Add** `createFeature(Features.web_vitals, 1.0, webJsSdk)` (or desired sample rate). |
| Tests | `DefaultSdkConfigTemplateTest`, `ConfigServiceImplTest`, etc. | Expect fixed feature list | Update counts / assertions when enum grows. |
| REST validation | Config controllers | Deserialize feature names to enum | Confirm `web_vitals` maps cleanly after enum addition. |

**MVP:** enum + default template row for `pulse_web_js` **before** relying on server-driven disable of vitals.

---

## 3. Ingestion / ClickHouse (`backend/ingestion/`)

| Touchpoint | Today | Action |
|------------|--------|--------|
| OTLP metrics tables | `otel_metrics_*` unified view exists | No schema change **if** metrics use standard OTel names + resource attrs; validate collector accepts histograms from browser exporter. |
| `pulse.type` | Often on spans/logs; metrics may use attributes | ADR: stamp `pulse.type` via **global metric attrs** (`getMetricGlobalAttrs`) or per-record attrs—must match query patterns. |

**MVP:** validate end-to-end with local Collector + ClickHouse (deploy skill); optional SQL doc for example queries.

---

## 4. Pulse UI (`pulse-ui/`)

| Touchpoint | Today | Action |
|------------|--------|--------|
| Session replay types | [`coreWebVitals` in types/mocks](../../../pulse-ui/src/services/sessionReplay/types.ts) | **Separate** from SDK OTLP path — mock/demo data for replay UX. |
| Dashboards / Vitals views | No unified “Web Vitals” from OTLP in grep | **Later:** charts querying `otel_metrics` filtered by `pulse.type` / metric name + `platform = web`. |

**MVP:** none (ingest-only). **Later:** product-defined screens.

---

## 5. AI agent (`pulse_ai/`)

| Touchpoint | Today | Action |
|------------|--------|--------|
| Metric / template registry | Interaction-focused `PulseType` filters | **Later:** register web vital metric names if EM/agent should answer perf questions. |

**MVP:** optional documentation only.

---

## 6. Android / iOS / RN SDKs

| Touchpoint | Action |
|------------|--------|
| `PulseAttributes.PulseTypeValues` ([Android](../../../pulse-android-otel/pulse-semconv/src/main/java/com/pulse/semconv/PulseAttributes.kt)) | **Document** `web_vital` (or chosen value) for parity **documentation**—mobile does not emit browser vitals. |

**MVP:** contract doc only ([04-contract-parity.md](./04-contract-parity.md)).

---

## 7. Planning / repo docs

| Touchpoint | Action |
|------------|--------|
| [`pulse-web-otel/README.md`](../../README.md) | Link `web-sdk-plan/v2-web-vitals/` after milestones settled. |
| Test run log | [`web-sdk-plan/agent-runtime/test-run-log.md`](../../web-sdk-plan/agent-runtime/test-run-log.md) | Append vitals E2E runs during implementation (sanity skill). |

---

## Prioritized summary

| Priority | Scope |
|----------|--------|
| **P0 — MVP** | Web SDK instrumentation + semconv; backend `Features.web_vitals` + default config row; unit + E2E tests; OTLP smoke to ClickHouse. |
| **P1** | UI dashboards for CWV; AI registry entries. |
| **P2** | Extra remote-config knobs (`config` blob per feature); subset of vitals; advanced bfcache strategy. |
