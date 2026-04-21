# Plan: `metricsToAdd` on Web (Android parity)

**Goal:** When `signals.metricsToAdd` is non-empty in merged remote config, the Web SDK records the same *kind* of OTel metric points Android does: on **export batches** of spans/logs, after attribute add, before attribute drop, using the shared **`MeterProvider`**.

**Non-goals (initial slice):** UI editor in `pulse-ui` (separate track); METRICS-scope rules (Android has a path for `MetricData` — low priority unless product requires).

---

## Phase 0 — Types & merge

- Extend `pulse-web-otel/src/types/remote-config.ts` `PulseSignalConfig` with `metricsToAdd: PulseMetricsToAddEntry[]` mirroring backend Kotlin shape:
  - `name`, `type` (discriminated union: counter / gauge / histogram / sum + flags), `condition: PulseSignalMatchCondition`, `target` (`name` | `attribute` + optional `shouldAddPropNameAsSuffix`), `attributesToPick?: PulseSignalMatchCondition[]`.
- **`mergePulseSdkConfig`:** normalize `condition` (and any nested conditions on `target`) with `normalizeSignalMatchCondition`; default `metricsToAdd: []`.

**Exit:** Typecheck + unit test deserialize/merge a minimal JSON fixture (can mirror `MetricsToAddPolymorphicJsonTest` on the server).

---

## Phase 1 — Instrument factory (mirror `createMeterRecorderFactory`)

- Add `src/sampling/metrics-to-add-recorder.ts` (name TBD): given `PulseMetricsToAddEntry`, return `(metricName: string) => (value: unknown, attrs: Attributes) => void` with an internal cache per **sanitized** metric name (reuse Android’s `PulseOtelUtils.sanitizeInstrumentationName` idea — port or call a small local helper).
- Map `PulseMetricsType` → `@opentelemetry/api` `Meter` builders (`counterBuilder`, `gaugeBuilder`, `histogramBuilder`, `upDownCounterBuilder`) matching Android branches (long vs double, monotonic, explicit buckets).
- **Counter** path: Android uses `+1` for counter type in `createMeterRecorderFactory` — match that for `PulseMetricsType.Counter`.

**Exit:** Vitest unit tests with a **noop / in-memory `MeterProvider`** (or mock meter) asserting `record` / `add` invoked with expected attributes.

---

## Phase 2 — Hook at export time (spans + logs)

- **Preferred alignment with Android:** implement inside the same conceptual place as `SampledSpanExporter` / `SampledLogRecordExporter` **before** delegating — i.e. a small `applyMetricsToAddFromSpans(readableSpans, config, meterProvider)` called from `SampledSpanExporter.export` (and logs equivalent), **after** session sampling decision if we want counts to reflect *exported* signals only — **match Android:** Android runs `addMetrics` inside `observerAndModifyData` **before** the `signalsToSample` / session filter on the modified list. Re-read `PulseSamplingSignalProcessors.sampleSession` and keep **identical ordering**: add attrs → **metrics** → drop attrs → **then** sample for export. On Web today, attribute add/drop run in **processors**; export gate does sampling + filters. Short-term options:
  - **(A)** Run `metricsToAdd` in **`SampledSpanExporter` / `SampledLogRecordExporter`** on the **pre-gate** batch (requires exporting wrapper to receive full batch before gate — today gate is inside wrapper). **Adjust:** run metrics in a **new** inner wrapper *before* `SampledSpanExporter`, or extend `SampledSpanExporter` to accept optional `metricsToAdd` runner.
  - **(B)** Run in **`ExportSamplingGate`** after filters but before per-signal rate — only if ordering matches product expectation (document delta if not).

**Recommendation:** Introduce **`SpanExporter` / `LogRecordExporter` decorator** `MetricsToAddSpanExporter` **inside** `sampling-exporters.ts` that wraps the **existing** `SampledSpanExporter` chain: order = `MetricsToAdd` → `SampledSpanExporter` → `PulseBrowserTraceExporter` (metrics see **all** ended spans that reach export, or only those passing gate — **product call**: default to **Android order** = metrics on signals **before** session sample filter; confirm with `PulseSamplingSignalProcessorsTest`).

- Wire `MeterProvider` from `createProviders` into the decorator (pass `() => meterProvider.getMeter(...)` from `sdk.ts` / `createProviders` closure).

**Exit:** E2E or unit test: synthetic span batch + config with one `metricsToAdd` counter → metric reader/export snapshot shows increment.

---

## Phase 3 — Semantics parity

- **`attributesToPick`:** port `buildAttributesFromPick` (regex on attribute keys).
- **`target`:** `Name` vs `Attribute` branches + `shouldAddPropNameAsSuffix`.
- **Invalid / non-numeric values:** same no-op / parse behavior as Android (`toLongOrNull` / `toDoubleOrNull`).

**Exit:** Port or translate selected cases from `PulseSamplingSignalProcessorsTest.kt` into Vitest.

---

## Phase 4 — Docs & UI (optional follow-ups)

- **`pulse-ui`:** extend `SignalsConfig` + add a minimal editor or “advanced JSON” surface for `metricsToAdd`.
- **`WEB-SDK-IMPLEMENTATION-M1.md`:** document export pipeline order diagram.

---

## Risks

- **Double counting** if metrics run on both processor path and export path — only one hook.
- **Cardinality:** attribute-derived metric names + high-cardinality labels — same operational warnings as Android.
- **Performance:** extra work per batch; keep recorder cache hot.

---

## Changelog

| Date | Change |
|------|--------|
| 2026-04 | Initial plan after `chore/web-sdk` merge; Web export sampling retained, `PulseSamplingProcessor` not reintroduced (export gate is canonical for session sampling). |
| 2026-04 | Phases 0–3 implemented: types + merge, `metrics-to-add-recorder.ts`, export decorators, log chain order `MetricsToAdd` outside keepalive for pagehide parity. |
