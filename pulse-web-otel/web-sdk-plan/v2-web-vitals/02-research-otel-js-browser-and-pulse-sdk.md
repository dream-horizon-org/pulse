# Research: OpenTelemetry JavaScript in the browser + Pulse Web SDK wiring

**Status:** Planning artifact (Phase B).  
**Prerequisites:** [01-research-otel-ecosystem-and-industry.md](./01-research-otel-ecosystem-and-industry.md).  
**Inputs for:** [03-touchpoints-matrix.md](./03-touchpoints-matrix.md), [ADR-web-vitals.md](./ADR-web-vitals.md).

---

## 1. OTel JS browser stack in this repo

### 1.1 Packages in use

From `pulse-web-otel/package.json`:

- `@opentelemetry/sdk-trace-web` — traces  
- `@opentelemetry/sdk-logs` — logs  
- `@opentelemetry/sdk-metrics` — metrics  
- `@opentelemetry/api` — global `trace`, `logs`, **`metrics`** accessors  

`web-vitals` is already a dependency; instrumentation should call its `onLCP`, `onINP`, `onCLS`, etc., and transform callbacks into OTel records.

### 1.2 Provider construction (`createProviders`)

[`src/exporters.ts`](../../src/exporters.ts):

1. Builds **`MeterProvider`** with a **`PeriodicExportingMetricReader`** whose interval matches **`DEFAULT_BATCH_OPTIONS.scheduledDelayMillis`** (same cadence as trace/log batching).
2. Wires **`PulseBrowserMetricExporter`** → OTLP HTTP **`/v1/metrics`** with API key + metering session headers.
3. Optionally wraps metrics with **`SampledPushMetricExporter`** (sampling gate) and **`BeforeSendMetricExporter`** (`beforeSendMetric` / generic `beforeSend`).
4. Optionally wraps with **`GlobalAttributeInjectingMetricExporter`** using **`getMetricGlobalAttrs`** so every data point gets session/screen/project attrs consistent with traces/logs.

**Implication:** Web Vitals should be emitted via **`Meter`** APIs (`histogram.record()`, etc.) so they reuse the same export, sampling, before-send, disk-buffer, and global-attribute paths—not a side-channel fetch.

### 1.3 Globals (`PulseWebSDK.bindGlobalProviders`)

After `createProviders`, [`sdk.ts`](../../src/sdk.ts) calls:

- `metrics.setGlobalMeterProvider(meterProvider)`

So any instrumentation installed **after** `bindGlobalProviders()` can use:

```ts
import { metrics } from "@opentelemetry/api";
const meter = metrics.getMeter("pulse.web.web_vitals", "1.0.0");
```

**Order note:** `installInstrumentations` runs **after** `bindGlobalProviders` in `finishStart`, so `metrics.getMeter` is safe inside `install()` of `WebVitalsInstrumentation`.

---

## 2. Config touchpoints

| Mechanism | Location | Role for Web Vitals |
|-----------|----------|---------------------|
| Static toggles | [`PulseWebConfig.instrumentations.webVitals`](../../src/types/config.ts) | `{ enabled: boolean }` — paired with feature gate (both must allow install). |
| Remote gate | [`PulseFeature.WEB_VITALS`](../../src/types/remote-config.ts) (`"web_vitals"`) | Server-driven kill switch; [`FeatureGate`](../../src/feature-gate.ts) defaults missing features to **enabled**. |
| Consent | [`PulseDataCollectionConsent`](../../src/types/config.ts) | If not `ALLOWED`, `start()` returns before providers/instrumentations — **no** vitals. |
| Export format | `config.export.format` | Affects all OTLP signals including metrics. |
| Before send | `beforeSendData.beforeSendMetric` / `beforeSend` | Can strip or redact metric exports at batch time (`BeforeSendMetricExporter`). |
| Sampling | [`ExportSamplingGate`](../../src/sampling/export-sampling-gate.ts) | May drop metric batches when session unsampled—same as other signals. |
| Disk buffer | `diskBuffering` | Failed metric exports can retry from IndexedDB like traces/logs. |

### 2.1 Instrumentation registry

[`InstrumentationRegistry`](../../src/instrumentation-registry.ts) maps **`InstrumentationKeys.WEB_VITALS`** → **`PulseFeature.WEB_VITALS`**.  
**Today:** `installAll()` only registers **session** instrumentation; **Web Vitals is not installed yet** (comment placeholder). Implementation adds `registerAndInstall(new WebVitalsInstrumentation(), InstrumentationKeys.WEB_VITALS)` in the same pattern as `InteractionInstrumentation`.

---

## 3. Lifecycle and teardown

| Event | Behavior |
|-------|----------|
| **`install`** | Register `web-vitals` listeners; create OTel instruments from `metrics.getMeter`. |
| **`uninstall`** | Remove listeners / call cancellation handles returned by `web-vitals` where applicable; do not leave `PerformanceObserver`s attached. |
| **`shutdown()`** | SDK calls `registry.uninstallAll()` then `meterProvider.forceFlush()` — vitals must not enqueue after uninstall. |
| **`pagehide`** (non-bfcache) | Existing listener flushes trace/log/meter and switches keepalive—ensures last metric batch can leave the tab. |
| **bfcache** (`pagehide` + `persisted`) | Listener early-outs (`!e.persisted`); sessions may restore—**web-vitals** has reporting modes for bfcache; ADR should cite whether to pause or use library defaults. |

---

## 4. Optional `SdkContext` extension

[`SdkContext`](../../src/types/instrumentation-registry.ts) currently exposes `tracer` and `logger` but **not** `meter`. Two options:

1. **Global only:** Use `metrics.getMeter` in instrumentation (no interface change).  
2. **Explicit:** Add `getMeter(instrumentationScope: string): Meter` to `SdkContext` or pass `MeterProvider` for testability.

**Recommendation:** Start with **globals** for parity with how `tracer`/`logger` are both on context **and** globally set; add `getMeter` on `SdkContext` if unit tests need injected fake `MeterProvider`.

---

## 5. Wiring diagram (text)

```text
PulseWeb.start(config)
  → consent check
  → build resource, FeatureGate, SamplingGate, exporters
  → createProviders → MeterProvider + PeriodicExportingMetricReader
  → bindGlobalProviders → metrics.setGlobalMeterProvider
  → InstrumentationRegistry.installAll()
       → WebVitalsInstrumentation.install(sdk)
            → metrics.getMeter("pulse.web.web_vitals")
            → onLCP / onINP / onCLS / … → histogram.record / ...
  → OTLP /v1/metrics → Collector → ClickHouse
```

---

## 6. What must stay disabled

If **`dataCollectionState`** is not allowed → **no** init (existing behavior).  
If **`gate.isEnabled("web_vitals")`** is false **or** **`instrumentations.webVitals.enabled === false`** → `registerAndInstall` returns false — **no** listeners.  
If user provides **`beforeSendMetric`** that drops all web vital metrics → instrumentation still runs but export pipeline drops points (test this).

---

## 7. References

- [@opentelemetry/sdk-metrics](https://github.com/open-telemetry/opentelemetry-js/tree/main/experimental/packages/opentelemetry-sdk-metrics) — `MeterProvider`, `PeriodicExportingMetricReader`  
- [`pulse-web-otel/src/exporters.ts`](../../src/exporters.ts)  
- [`pulse-web-otel/src/sdk.ts`](../../src/sdk.ts)  
