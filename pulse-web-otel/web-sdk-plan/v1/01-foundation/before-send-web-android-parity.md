# `beforeSend` — Web ↔ Android parity, main-thread semantics, implementation plan

**Status:** **Wired** in `src/exporters.ts` via `BeforeSendSpanExporter` / `BeforeSendLogRecordExporter` / `BeforeSendMetricExporter` (`src/exporters/before-send-exporters.ts`). Public config key: **`PulseWebConfig.beforeSendData`** (`PulseWebBeforeSendConfig` in `src/before-send.ts` — function or callback object). Validation: `validateBeforeSendConfig` from `validateConfig()`. Unit tests: `src/__tests__/before-send-exporter.test.ts`.

**Export chain (batch / reader → wire)** — `beforeSend` is **outermost** (runs first on each flush), **before** export-time sampling / metrics-to-add / global metric attrs / browser+disk:

| Signal | Chain (delegate order, inner → outer from OTLP) |
|--------|---------------------------------------------------|
| Traces | `PulseBrowserTrace` ← `SampledSpan` ← `MetricsToAddSpan` ← **`BeforeSendSpan`** ← `BatchSpanProcessor` |
| Logs | `PulseBrowserLog` ← `SampledLog` ← `KeepaliveFetch` ← `MetricsToAddLog` ← **`BeforeSendLog`** ← `BatchLogRecordProcessor` |
| Metrics | `PulseBrowserMetric` ← `SampledPushMetric` ← `GlobalAttributeInjecting` ← **`BeforeSendMetric`** ← `PeriodicExportingMetricReader` |

This document also captures **runtime semantics**, **performance expectations**, and **API parity** with Android.

**Android source of truth:** `pulse-android-api/.../PulseBeforeSendData.kt`, `pulse-android-sdk-internal/.../beforesend/PulseBeforeSend*Exporter.kt`, wiring in `PulseSDKInternal.kt` (wraps span/log/metric exporters).

---

## 1. Threads, batching, and processors — web vs Android

### Android

- Java/Kotlin telemetry runs on **app and background executor threads** depending on lifecycle.
- `PulseBeforeSend*Exporter.export(...)` runs **synchronously** in the exporter’s call stack when a batch is flushed — typically **off the UI thread**, but still **blocking that worker** until callbacks return and the delegate export proceeds.

### Web (browser)

- There is **no general-purpose background thread** for page JavaScript. Unless the SDK explicitly uses a **Web Worker** (Pulse web SDK today does **not** for traces/logs/metrics export), **instrumentation, span/log processors, batch flush, serialization, and user hooks run on the main thread’s event loop** (same thread as React, input, paint for that document).
- **OpenTelemetry JS** `BatchSpanProcessor` / `BatchLogRecordProcessor` schedule `exporter.export()` via timers / microtasks; when that runs, it is still **main-thread** work.
- **Network I/O** (`fetch`) is asynchronous, but **building the OTLP body** (and any synchronous `beforeSend`) is usually **CPU-bound on the thread that invoked `export()`** — almost always the **main thread**.

### Practical consequence (performance contract)

Treat `beforeSend` like **hot-path code**:

- Prefer **O(1)** work: small attribute redaction, simple filters.
- **Avoid** blocking I/O (`fetch`, IndexedDB except where the SDK already async-wraps, large synchronous `JSON.stringify`), unbounded regex on large strings, contended locks, or anything that can run for tens of milliseconds per batch.
- **Stricter than Android in user impact:** the same CPU time on web is more likely to **degrade INP / frame time** because it shares the UI thread.

**Industry pattern (context, not a Pulse dependency):** Many RUM vendors keep capture + export prep on the main thread; some **offload heavy CPU** selectively (e.g. Sentry Session Replay **defaults to a Web Worker for compression** to reduce main-thread jank, with a CSP-aware fallback to main-thread compression). That does **not** mean all Sentry traffic is off-thread — it illustrates that **worker offload is explicit engineering**, not the default for all browser SDK work.

### Documentation promise to integrators

Document explicitly:

1. `beforeSend` may run on the **main thread** during batch export.
2. Slow callbacks **delay flush** and can **hurt responsiveness**; keep them minimal.
3. **Async work does not defer the drop/allow decision** for the current batch: the exporter must decide **synchronously** whether to forward or drop each item (same model as Android’s synchronous exporter API).

---

## 2. Android `beforeSend` semantics (must match on web)

From `PulseBeforeSendData`:

1. **Order:** `beforeSend` (generic, all signal kinds) **first** → then signal-specific `beforeSendSpan` / `beforeSendLog` / `beforeSendMetric`.
2. **Drop:** Any step returning **`null`** drops that item (generic `null` skips typed hooks).
3. **Wrong type after generic:** If `beforeSend` returns a non-null value that is **not** the expected concrete type for that exporter (e.g. log-shaped object where a span was expected), Android **drops** the item (`mapNotNull` + type check). Web must do the **same** for parity and safety.

Reference implementations:

- `PulseBeforeSendSpanExporter.kt` — generic → `beforeSendSpan`, type guard.
- `PulseBeforeSendLogExporter.kt` — same for logs.
- `PulseBeforeSendMetricExporter.kt` — same for metrics.

---

## 3. Pipeline precedence — `SignalFilterProcessor` vs `beforeSend`

These are **different stages**; both coexist.

| Stage | Owner | When | Role |
|--------|--------|------|------|
| Span / log **processors** (e.g. `PulseGlobalAttributesProcessor`, `SignalFilterProcessor`) | SDK + **remote config** (`SdkConfigFetcher` / `PulseSignalConfig`) | During span/log lifecycle (`onStart` / `onEnd` / `onEmit` as applicable) | Fleet rules: inject/drop attributes, pattern-based key drops, etc. |
| **Hooks from `beforeSendData`** | **App** (`PulseWeb.start` → `PulseWebConfig.beforeSendData`) | **Exporter** `export()` on a **batch** of OTLP-ready items | Last-mile app policy: scrub, drop routes, etc. |

**Documented order for a signal that is eventually exported:**

`Global attrs processor` → `SignalFilterProcessor` (remote-driven) → … → **batching** → **`beforeSend` at export** → export-time sampling (`Sampled*` wrappers) → metrics-to-add wrappers (web) → transport / disk buffer layer.

**Implications:**

- App `beforeSend` sees the signal **after** remote-driven processor mutations.
- If a signal **never reaches export** (dropped earlier, never batched), **`beforeSend` does not run** for it.
- **`beforeSend` runs before** export-time session sampling in Android’s wiring (`PulseBeforeSendSpanExporter` wraps `SampledSpanExporter`’s delegate chain — see `PulseSDKInternal.kt`). Web should mirror that **relative order** when wrapping `createProviders` exporters (see §4).

---

## 4. Implementation plan (web, Android parity)

### 4.1 Public API (TypeScript)

**Today:** `PulseWebConfig.beforeSendData?: PulseWebBeforeSendConfig` — either a **single function** `(signal: unknown) => unknown | null` **or** a **callback object** (Android `PulseBeforeSendData` shape).

**Parity target:** Support the same **two-level** pattern as Android:

- Optional **generic** `beforeSend(signal)` (narrow union or `unknown` with documented kinds).
- Optional **typed** `beforeSendSpan` / `beforeSendLog` / `beforeSendMetric` with OTel JS types (`ReadableSpan`, `ReadableLogRecord`, `ResourceMetrics` or a documented view).

Accept either a **single function** (current) **or** a **callback object** (Android `PulseBeforeSendData` shape) — exact union type is an implementation detail; docs must state **dispatch order** and **null / wrong-type = drop**.

**Immutability note:** OTel JS readables may be frozen or shared; document whether **in-place mutation** is supported or whether integrators should return **cloned** structures if the implementation adds copy-on-write helpers later.

### 4.2 Code placement (done)

1. **Exporter wrappers:** `src/exporters/before-send-exporters.ts` — generic → type guard → typed hook; `null` / wrong type drops.

2. **`createProviders` (`src/exporters.ts`)** — `ResolvedBeforeSend` on `ExporterConfig.beforeSendData`; `sdk.ts` passes `resolveBeforeSend(config.beforeSendData)`. Wrappers are **outermost** on the span/log/metric heads (see table above).

3. **Disk buffer** — `beforeSend` runs **before** `PulseBrowser*` exporters, so dropped signals are **not** written to IndexedDB.

4. **Consent** — unchanged: `DENIED` / `PENDING` → no providers → **`beforeSend` never runs**.

### 4.3 Tests & docs

- `src/__tests__/before-send-exporter.test.ts` — span/log/metric exporter behavior + `validateBeforeSendConfig`.
- `src/__tests__/integration-simplified-init.test.ts` — invalid `beforeSendData` throws; valid function / object accepted (`createProviders` still mocked in that suite).
- Plan / agent context links maintained from `pipeline.md` and `WEB-SDK-AGENT-CONTEXT.md`.

### 4.4 Non-goals (this milestone)

- **Web Worker** offload for `beforeSend` or OTLP encoding (would change synchronous contract).
- Replacing **`SignalFilterProcessor`** with app hooks (different product surface).

---

## 5. Summary table

| Topic | Android | Web (target parity) |
|--------|---------|---------------------|
| Hook surface | `PulseBeforeSendData` class methods | Callback object or single function (`PulseWebBeforeSendConfig`) on **`beforeSendData`** |
| Invocation | Exporter `export`, per batch item | Same |
| Order | Generic → typed; `null` drops | Same |
| Wrong type after generic | Drop | Same |
| vs remote processor | Processors first, `beforeSend` at export | Same |
| Thread | Worker / export executor (varies) | **Main thread** unless explicitly offloaded later |
| Performance | Don’t block export | Don’t block export **+** don’t jank the UI |

---

## References (repo paths)

- Android API: `pulse-android-otel/pulse-android-api/src/main/java/com/pulse/android/api/otel/PulseBeforeSendData.kt`
- Android tests: `pulse-android-otel/pulse-android-sdk-internal/src/test/java/com/pulse/android/sdk/internal/PulseBeforeSendTest.kt`
- Web config: `pulse-web-otel/src/config.ts`, `pulse-web-otel/src/types/config.ts`
- Web export assembly: `pulse-web-otel/src/exporters.ts`, `pulse-web-otel/src/sampling/sampling-exporters.ts`
- Web processors: `pulse-web-otel/src/processors/signal-filter-processor.ts`, `pulse-web-otel/src/sdk.ts` (processor list)
