# `metricsToAdd` — what it is, who uses it, how it flows

Audience: engineers wiring **remote SDK config** and SDK parity (Android ↔ Web).  
Android reference: `pulse-android-otel/pulse-sampling/core/.../PulseSamplingSignalProcessors.kt` (`addMetrics`, `createMeterRecorderFactory`, `getMetricsToAddConfig`).  
Backend models: `backend/server/.../SignalsConfig.java`, `MetricsToAddEntry.kt`, polymorphic `MetricsToAddTarget` / `MetricsType`.

---

## In simple terms

**`signals.metricsToAdd`** is a list of rules: *when a span or log matches this condition, also record a custom OpenTelemetry metric* (counter, gauge, histogram, or sum) using data from that signal.

- Traces and logs still go through the normal OTLP **trace** / **log** exporters (after sampling and attribute rules).
- The extra numbers go through the app’s **`MeterProvider`** and out the **metrics** OTLP pipeline — same path as other RUM metrics.

So it is **configuration-driven analytics**: change counts or aggregates **without shipping a new app**, as long as the signals already carry the attributes you match on.

---

## How teams use it

Typical uses:

- **Count** occurrences of a span name or log body pattern (e.g. every `payment.completed` log → counter +1).
- **Record a numeric attribute** (e.g. duration or amount) into a **histogram** or **sum**.
- Attach **dimensions** on the metric point by **copying** a subset of span/log attributes (`attributesToPick` — key patterns).

---

## How it is configured in the backend

1. The project’s **SDK config JSON** (Pulse “active config” document) includes **`signals.metricsToAdd`**: an array of entries with metric **name**, **type** (counter / gauge / histogram / sum and flags), **condition** (scopes, sdks, name regex, attribute props), **target** (`name` vs `attribute`), and optional **attributesToPick**.

2. **pulse-server** parses and stores this under the same config version as sampling, features, `attributesToAdd` / `attributesToDrop`, etc. (`SignalsConfig.metricsToAdd` in Java).

3. The **GET active config** API returns the full JSON to the SDK (same payload the mobile SDKs consume).

---

## Pulse UI today

The **Sampling / SDK Config** editor in `pulse-ui` models **`signals.attributesToAdd`**, **`attributesToDrop`**, sampling rules, features, and infra URLs. Its TypeScript **`SignalsConfig`** type **does not currently include `metricsToAdd`**, and there is no dedicated form for it.

So in practice, `metricsToAdd` is usually:

- Absent or **empty** (feature off), or  
- Present because the JSON was written via **API**, migration, admin tooling, or a future UI.

---

## How it is plugged in on the Android SDK

1. **Init:** `PulseSamplingSignalProcessors` is constructed with **`PulseSdkConfig`**, current SDK name, and a **`MeterProvider`**.

2. **Per export batch:** `SampledSpanExporter` / `SampledLogExporter` call `sampleSpansInSession` / `sampleLogsInSession`.

3. **Per signal:** After optional **`attributesToAdd`**, **`addMetrics`** runs: for each `metricsToAdd` entry whose **condition** matches the signal, the code resolves the **target** (signal name string vs attribute values), builds optional **picked** attributes, and invokes the right OTel instrument (`counter.add`, `gauge.set`, `histogram.record`, etc.). Instruments are **cached** per sanitized metric name.

4. Then **`attributesToDrop`** and **session / `signalsToSample`** filtering run; then spans/logs are exported.

**Web SDK (`pulse-web-otel`):** reads merged `signals.metricsToAdd`, records via the shared `MeterProvider` on trace/log export batches **before** the export sampling gate (see `MetricsToAddSpanExporter` / `MetricsToAddLogRecordExporter` in `sampling-exporters.ts`). UI editor for the JSON field is still optional.

---

## Related docs

| Doc | Purpose |
|-----|---------|
| `METRICS-TO-ADD-WEB-PLAN.md` | Phased plan to implement Web parity |
| `SAMPLING-RULES-WEB-PARITY.md` | Session **sampling rule** names vs Android |
| `WEB-SDK-AGENT-CONTEXT.md` | Web file map + parity ground rules |

---

## Changelog

| Date | Change |
|------|--------|
| 2026-04 | Author `METRICS-TO-ADD.md` from Android + backend + UI audit. |
