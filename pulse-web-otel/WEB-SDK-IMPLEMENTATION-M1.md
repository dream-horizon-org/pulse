# Pulse Web SDK — M1 Implementation

Package: `@dreamhorizon/pulse-web`  
Status: **M1 foundation shipped in tree — 123 Vitest tests (`src/__tests__/*.test.ts`) + 63 Playwright tests (`examples/ecommerce-demo/e2e/m1.spec.ts`; run with `--project=chromium` as needed)**

This document is the **living record of what the code does today**, how it was tested, and **gaps vs** `web-sdk-plan/v1/MILESTONES.md` / `WEB-SDK-AGENT-CONTEXT.md`. It is updated alongside implementation (last reviewed from codebase + internal code review pass).

---

## Ground rule — match Android SDK core logic

**Product behavior should align with `pulse-android-otel`, not only with milestone text.** Milestones and phase docs can lag; when they conflict with Android, implementation and tests should follow Android unless we document a deliberate browser-only difference.

**Planned convergence (non-exhaustive):**

| Area | Android reference | Web today | Target |
|------|-------------------|-----------|--------|
| Session sampling | `PulseSamplingSignalProcessors` — filters at **export** | **`ExportSamplingGate` + `Sampled*Exporter` wrappers** in `createProviders` — same idea: filter batch before OTLP; `sessionSampleRate: 0` ⇒ empty batch ⇒ no HTTP **(implemented)** |
| Sampling rules | `PulseSessionConfigParser` + contextual `matches` + `signalsToSample` | First `sampling.rules[]` entry whose `sdks` includes `pulse_web_js` | Match Android rule precedence and per-signal `signalsToSample` when config shape is shared |
| Signal filter | Full pipeline in sampling processors + config | Logs partial; traces limited | Match Android `SignalFilterProcessor` / attr add-drop behavior per scope |
| Intentional web-only | — | keepalive JSON log flush on `pagehide`, protobuf default, IndexedDB replay | Keep; document in this table |

See also: `web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` → **Ground rule — parity with Android SDK**.

---

## Milestone alignment (M1 vs what shipped)

The **M1 milestone table** in `MILESTONES.md` lists foundation only (session skeleton, OTLP, IndexedDB buffer, `SdkConfigFetcher`). The **current package intentionally includes adjacent plumbing** that the plan schedules for later milestones, because the init path needs hooks early:

| Area | Strict M1 wording | In repo now |
|------|-------------------|-------------|
| Remote config + sampling + signal filter + feature gate | M2 table in milestones | **Wired in `sdk.ts`** — `FeatureGate`, **`ExportSamplingGate`** (export-time), `SignalFilterProcessor`, `SdkConfigFetcher` |
| Custom events / manual errors | Useful for demo + parity | **`PulseWeb.trackEvent`**, **`reportException`**, **`trackNonFatal`**, **`reportDeviceCrash`** on core export path |
| React | M2 `PulseProvider` | **M1 doc / demo:** `PulseErrorBoundary` only (`src/integrations/react/`), separate `@dreamhorizon/pulse-web/react` entry |

**Spec deltas to track (not bugs by default):**

1. **OTLP wire format:** `createProviders` uses **protobuf** unless `config.export.format === 'json'` (`src/exporters.ts`) — aligned with `MILESTONES.md`.
2. **Sampling:** **Export-time** via `ExportSamplingGate` + `SampledSpanExporter` / `SampledLogRecordExporter` / `SampledPushMetricExporter` + keepalive path filtering — aligned with Android “drop before OTLP”. Session draw uses **one** `Math.random()` per SDK init (same random for per-signal `signalsToSample` rates).
3. **`session` feature flag:** Remote config can disable the `session` feature → `SessionInstrumentation` never installs → no `session.start` / `session.end`. Confirm product intent (identity vs optional telemetry).
4. **`beforeSend`:** Declared on `PulseWebConfig` but **not invoked** anywhere in the pipeline yet (planned for M3 in milestones).
5. **`session.start` on reload:** When the SDK **reuses** a session (`SessionProvider` clone/reuse path), `emitInitialSession` may skip a new `session.start` — compare to exit-criteria wording “on init” if dashboards expect one start per navigation.

---

## Flow Diagram

```
PulseWeb.start(config)
         │
         ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │  Step 1: validateConfig()                                       │
  │  Step 2: Consent gate — DENIED/PENDING → early return (no-op)  │
  │  Step 3: SessionProvider (inactivity timeout, BFCache guard)    │
  │  Step 3.5: getOrCreateInstallationId() [3-tier persistence]     │
  │  Step 4: buildResource() → OTEL Resource                        │
  │  Step 5: SdkConfigFetcher.loadCached() → PulseSdkConfig        │
  │  Step 6: FeatureGate + ExportSamplingGate (export) + SignalFilterProcessor │
  │  Step 7: GlobalAttributesProcessor (+ optional LogRecordLifecycleDebug) │
  │  Step 8: createProviders() → TracerProvider, LoggerProvider,    │
  │          MeterProvider (PeriodicExportingMetricReader)           │
  │  Step 9: Register global providers                              │
  │  Step 10: InstrumentationRegistry.installAll() (session only)   │
  │  Step 11: configFetcher.fetchInBackground()                     │
  │  Step 12: Emit sdk.init span                                    │
  │  Step 13: Emit pulse.app.installation.start (first install only)│
  └─────────────────────────────────────────────────────────────────┘
         │
         │  Signal path (for every PulseWeb.trackEvent / reportException / instrumentation)
         ▼
  ┌──────────────────────────────────────────────────────────────────────┐
  │                          Signal Pipeline                             │
  │                                                                      │
  │  SDK API call (trackEvent / reportException / auto-instrumentation)  │
  │          │                                                           │
  │          ▼                                                           │
  │  GlobalAttributesProcessor (adds session.id, installation.id,        │
  │    url.path, platform='web', browser.name, os.name, etc.)            │
  │          │                                                           │
  │          ▼                                                           │
  │  SignalFilterProcessor (attr add/drop + log drop gates from remote)  │
  │          │                                                           │
  │          ▼                                                           │
  │  BatchSpanProcessor / BatchLogRecordProcessor                        │
  │  (delay: 5s default / 200ms test mode; queue: 2048; batch: 512)      │
  │          │                                                           │
  │          ▼                                                           │
  │  Sampled* exporters (session + signalsToSample + critical bypass)   │
  │          │                                                           │
  │          ▼                                                           │
  │  PulseBrowser* exporters (JSON or protobuf via otlp-transformer)     │
  │  Optional gzip (CompressionStream); retry; optional IndexedDB buffer │
  │  Headers: X-API-KEY + X-Pulse-Metering-Session-ID                    │
  │          │                                                           │
  │          ▼                                                           │
  │  OTEL Collector :4318/v1/{traces|logs|metrics}                       │
  │          │                                                           │
  │          └──▶ OTEL Collector → storage (e.g. ClickHouse) per deploy wiring │
  └──────────────────────────────────────────────────────────────────────┘

  Pagehide / unload path (`persisted=false`):
  window `pagehide`
          │
          ├── Logger: `forceFlush()` → batched path ends with **`fetch(..., { keepalive: true })`**
          │   (`KeepaliveFetchLogExporter` in `src/exporters.ts`) so logs can leave the tab during unload
          ├── Traces + metrics: `forceFlush()` on providers (XHR/protobuf path for normal batches unchanged)
          └── `SessionProvider` emits `session.end` (`session.duration_ms`, `session.end_reason`, etc.)
```

### Remote config and sampling refresh (M1 behavior)

`SdkConfigFetcher.loadCached()` supplies merged config at `PulseWeb.start()`. **`fetchInBackground()` does not rebuild `FeatureGate` or `ExportSamplingGate`**, so feature flags and export-time sampling stay fixed until a **full page reload**. Treat live gate updates as follow-on product work, not an undocumented bug.

### Session rules vs Android `Context`

Web picks the first `sampling.rules[]` entry whose `sdks` includes `pulse_web_js` (`resolveSessionSamplingRate`). Android matches rules against app **`Context`** keys. There is no identical browser key on the wire; parity is **best-effort** until remote rules can express the same dimensions for web.

### Critical policies — Android audit

`criticalSessionPolicies` / `criticalEventPolicies` appear on Android’s **remote JSON model** (`PulseSamplingConfig.kt`). In-repo **`PulseSamplingSignalProcessors.sampleSession` does not read `alwaysSend` / critical policies** on the sampled export path. The **Web** SDK applies `alwaysSend` in `ExportSamplingGate` as a **session bypass** — document as a deliberate delta until Android wires the same behavior.

---

## Implemented modules (`pulse-web-otel/src/`)

| Path | Role |
|------|------|
| `index.ts` | Public exports: `PulseWeb`, config types, `PulseDataCollectionConsent`, `SDK_VERSION`, `PulseWebSemconv` |
| `sdk.ts` | Singleton lifecycle, providers, registry, public logging APIs, `shutdown` |
| `config.ts` | `PulseWebConfig`, `InstrumentationConfig`, validation, consent enum |
| `consent.ts` | `isDataCollectionAllowed` |
| `session.ts` | Installation id (3-tier), `SessionProvider`, rotation, BFCache / clone semantics |
| `resource.ts` | Static browser resource + `extractProjectId` |
| `remote-config.ts` | `SdkConfigFetcher`, cache, version merge, background fetch |
| `feature-gate.ts` | Remote feature flags |
| `instrumentation-registry.ts` | Install/uninstall; **only** `SessionInstrumentation` today |
| `instrumentations/session.ts` | `session.start` / `session.end` logs from `SessionProvider` events |
| `exporters.ts` | `createProviders`, batch processors, pagehide wiring, metric reader |
| `exporters/pulse-browser-otlp-exporters.ts` | Browser OTLP exporters |
| `exporters/otlp-transport.ts` | XHR transport, gzip, build chain |
| `exporters/pulse-retrying-transport.ts` | Retry wrapper |
| `exporters/wrap-log-exporter-lifecycle-debug.ts` | Debug wrapper for log exporter |
| `processors/global-attrs-processor.ts` | Dynamic attrs on spans/logs + metric helper |
| `sampling/export-sampling-gate.ts` | `ExportSamplingGate` — session draw + `signalsToSample` + critical bypass |
| `sampling/sampling-exporters.ts` | `SampledSpanExporter`, `SampledLogRecordExporter`, `SampledPushMetricExporter` |
| `types/sampling.ts` | `PulseSignalScope` |
| `utils/sampling-signal-match.ts` | `pulseSignalConditionMatches`, `attrsToStringMap` |
| `utils/session-sampling-rate.ts` | `resolveSessionSamplingRate`, `sessionRuleMatchesWeb`, `logRecordBodyAsString`, critical policies |
| `processors/signal-filter-processor.ts` | Remote signal rules (log-focused) |
| `processors/log-record-lifecycle-debug-processor.ts` | Optional pipeline debug |
| `persistence/indexed-db.ts` | `IdbSignalBuffer` |
| `persistence/drain-buffered-exports.ts` | Replay failed exports on next start |
| `utils/otlp-gzip.ts`, `utils/compression.ts`, `utils/ua-parser.ts`, `utils/error-stack.ts` | Transport + UA + stack filename |
| `semconv.ts`, `version.ts` | Constants / build version |
| `integrations/react/PulseErrorBoundary.tsx`, `integrations/react/index.ts` | React error boundary entry |

---

## What Has Been Implemented (M1)

### 1. SDK Core (`src/sdk.ts`)


| Feature                        | Details                                                                                                                                                                                |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Singleton guard                | `PulseWebSDK.getInstance()` — `start()` is a no-op if already initialized. Handles React StrictMode double-invoke.                                                                     |
| Config validation              | `validateConfig()` throws on missing `endpointBaseUrl`, `apiKey`, `serviceName`                                                                                                        |
| Consent gate                   | `PulseDataCollectionConsent.DENIED` or `PENDING` → zero signals, zero network calls                                                                                                    |
| Disk buffering (opt-in)        | `diskBuffering.enabled` → async `drainBufferedOtlpExports()` replays IndexedDB rows against `/v1/*`, then normal init (`finishStart`). Shared `IdbSignalBuffer` passed into exporters. |
| Init sequence                  | Validate → consent → (optional drain) → session → install ID → resource → remote config → processors → providers → instruments → background fetch → `sdk.init` + first-install log     |
| Metering session ID            | `crypto.randomUUID()` — stable UUID per page load, sent as `X-Pulse-Metering-Session-ID` on every OTLP request (mirrors Android)                                                       |
| `sdk.init` span                | Short-lived span on every successful init — `pulse.type` = SDK init (`semconv`); used as pipeline heartbeat in M1 exit criteria (naming differs from literal string “heartbeat”)          |
| `pulse.app.installation.start` | Log emitted on first-ever install only — detected via `wasNewInstallation()`                                                                                                           |
| `shutdown()`                   | Awaits `forceFlush()` on all three providers, `InstrumentationRegistry.uninstallAll()`, `SessionProvider.shutdown()`                                                                      |
| Debug: log record lifecycle    | `config.debugLogRecordLifecycle === true` → `LogRecordLifecycleDebugProcessor` stages + optional `wrapLogExporterLifecycleDebug` on the log exporter; **verbose `console.log`** — dev only |


### 2. Public API (`src/sdk.ts`)


| Method                             | Signal type | Body                                                     | Key attributes                                                                                                    |
| ---------------------------------- | ----------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `trackEvent(name, attrs?)`         | Log         | event name (body)                                        | Gated on **remote feature** `custom_events` (`FeatureGate`). Attrs: `pulse.type=custom_event`, `event.name=pulse.custom_event`, plus caller attrs                                 |
| `reportException(error, attrs?)`   | Log         | `error.message`                                          | **Not** feature-gated in `sdk.ts`. Attrs: `pulse.type=non_fatal`, `exception.*`, `non_fatal.is_manual=true`                                                                       |
| `trackNonFatal(name, attrs?)`      | Log         | event name (body)                                        | **Not** feature-gated in `sdk.ts`. Attrs: `pulse.type=non_fatal`, `non_fatal.type`, `non_fatal.is_manual=true`                                                                    |
| `reportDeviceCrash(error, attrs?)` | Log         | error message                                            | `pulse.type=device.crash`, `exception.*`, `error.filename` (from stack); used by `PulseErrorBoundary`                                                                             |
| `setScreenName(name)`              | —           | sets global attr `screen.name` on all subsequent signals |                                                                                                                   |
| `isInitialized()`                  | —           | returns boolean                                          |                                                                                                                   |


> **Android parity note:** `trackEvent` emits a **log** (not a span) matching Android SDK behaviour. `reportException` body is the error message (not the literal string `'non_fatal'`). `trackNonFatal` is a named non-fatal variant without a stack trace.

### 2b. React entry (`@dreamhorizon/pulse-web/react`)


| Export               | Role                                                                                                   |
| -------------------- | ------------------------------------------------------------------------------------------------------ |
| `PulseErrorBoundary` | Class component; `componentDidCatch` calls `PulseWeb.reportDeviceCrash` then rethrows / shows fallback |


Import from `@dreamhorizon/pulse-web/react` (separate bundle entry in `tsup`). `react` is an optional peer dependency. The ecommerce demo aliases this subpath in Vite so it resolves to the workspace package.

### 3. Session Management (`src/session.ts`)


| Feature             | Details                                                                             |
| ------------------- | ----------------------------------------------------------------------------------- |
| Session ID          | UUID v4, regenerated on inactivity timeout (default: 30 min)                        |
| Inactivity timeout  | `inactivityTimeoutMs` (default 30 min) — also **`maxSessionLifetimeMs`** (default 4 h) and **`pageHiddenTimeoutMs`** (default 15 min) on `SessionProvider` |
| BFCache guard       | `pagehide` with `persisted=true` → updates activity timestamp, does NOT end session |
| Clone / reload reuse | `sessionStorage` clone flag pattern: duplicated tab or certain reload paths can reuse session id — see `session.ts` |
| `session.start` log | Emitted on SDK init with `session.id`, `installation.id`, `platform=web`            |
| `session.end` log   | Emitted on `pagehide` (non-BFCache), inactivity/max-lifetime rotation, shutdown — attrs include **`session.duration_ms`** (derived from `durationNs` in provider), `session.end_reason`, and `screens_visited` where applicable |
| Session rotation    | `onSessionChange` callback, emits `session.end` + new `session.start` on rotation   |


### 4. Installation ID (`src/session.ts`)

3-tier persistence — silently falls back when storage is unavailable (e.g., Safari ITP, privacy mode):


| Tier            | Storage             | Key                     |
| --------------- | ------------------- | ----------------------- |
| 1 (preferred)   | `localStorage`      | `pulse_installation_id` |
| 2 (fallback)    | `sessionStorage`    | `pulse_installation_id` |
| 3 (last resort) | Module-level memory | —                       |


`wasNewInstallation()` returns `true` only when the ID was created fresh (not read from storage). Used to gate the `pulse.app.installation.start` log — emitted once per device, never on reload.

### 5. OTLP export stack (`src/exporters.ts` + `src/exporters/*`)

Exports use **custom browser classes** (`PulseBrowserTraceExporter`, `PulseBrowserLogExporter`, metric factory) built on `@opentelemetry/otlp-exporter-base` with serializers from `@opentelemetry/otlp-transformer` — not the Node-oriented `@opentelemetry/exporter-*-otlp-proto` HTTP clients. That keeps Vite/browser bundles working and still supports **JSON** and **protobuf** bodies.


| Setting         | Default                                        | Notes                                                                                                                                                              |
| --------------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Format          | **`protobuf` if `format` omitted** (`useProtobuf = config.format !== "json"`) | Set `export.format: 'json'` for `application/json` (demo E2E, local DevTools).                                                                                      |
| Compression     | Gzip when `config.export.compression !== 'none'` (default gzip path) | If `CompressionStream` is missing, gzip wrapper is skipped. E2E `.env.test` uses `VITE_PULSE_COMPRESSION=none` for readable bodies.                                |
| Transport       | **Traces/metrics:** XHR chain in `otlp-transport.ts` | **Logs (normal batches):** same XHR stack. **Logs (`pagehide` flush path):** inner exporter bypasses XHR for a **keepalive `fetch`** (`KeepaliveFetchLogExporter`). |
| Auth header     | `X-API-KEY`                                    | Required                                                                                                                                                           |
| Metering header | `X-Pulse-Metering-Session-ID`                  | Stable UUID for the page load (Android parity)                                                                                                                     |


**Exporter init ordering:** `OTLPExporterBase` runs `onInit` during `super()`. Pulse subclasses **lazy-create** the transport in `send()` so `useGzip` / disk options are never read before the constructor finishes.

**Batch settings (defaults, overridable via `config.export.batch`):**

- `scheduledDelayMillis`: 5000 ms  
- `maxQueueSize`: 2048  
- `maxExportBatchSize`: 512  
- `exportTimeoutMillis`: 30000 ms

`window` `pagehide` (`persisted=false`) registers `forceFlush()` on trace, log, and meter providers inside `createProviders()`.

**Disk buffering (`config.diskBuffering`):** When `enabled`, failed export payloads are stored in IndexedDB (`src/persistence/indexed-db.ts`). On the next `start()`, `drainBufferedOtlpExports` (`src/persistence/drain-buffered-exports.ts`) replays stored envelopes with `**fetch`** (separate from live-export XHR), then deletes rows on HTTP 2xx. Optional `maxSizeBytes` / `maxAgeMs` prune the store.

### 6. Remote Config (`src/remote-config.ts`)


| Feature                    | Details                                                           |
| -------------------------- | ----------------------------------------------------------------- |
| Config URL auto-derivation | `:4318` collector URL → `:8080` server URL automatically          |
| Cache                      | Stored in `localStorage` as `pulse_sdk_config` (JSON)             |
| Version-gated writes       | Only writes to localStorage when fetched version > cached version |
| Background fetch           | `fetchInBackground()` — fire-and-forget, does not block init      |
| Default config             | `DEFAULT_SDK_CONFIG` used when no cached config and fetch fails   |


### 7. Feature Gate (`src/feature-gate.ts`)

Remote config `features[]` array controls per-signal enable/disable by `featureName` + `sessionSampleRate` per SDK (`pulse_web_js`). Defaults to enabled when the feature is absent from the config.

### 8. Processors


| Processor                   | Role                                                                                                                                                                                                                                                      |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GlobalAttributesProcessor` | Injects `session.id`, `installation.id`, `screen.name`, `url.path`, `page.url`, `browser.name`, `browser.version`, `os.name`, `os.version`, `device.type`, `network.connection.type`, `rum.sdk.version`, `project.id`, `platform=web` on every span + log |
| *(removed)* `PulseSamplingProcessor` | Replaced by **export-time** sampling (see `sampling/*`) — no `pulse.sampled` tag-only path                                                                 |
| `SignalFilterProcessor`     | Remote `signals` config: log attribute drops / log drops; **trace span attribute drops are not implemented** (`onEnd` is a no-op for mutation — see source comment)                                                                                      |


### 9. OTEL Collector Config (`backend/ingestion/otel-collector.yaml`)

`X-Pulse-Metering-Session-ID` added to CORS `allowed_headers` — required for browser cross-origin OTLP requests. Without this the browser silently drops all OTLP exports.

### 10. Ecommerce demo instrumentation

`trackEvent` calls wired to CTAs in `examples/ecommerce-demo/`. The **error demo** route (`src/routes/ErrorDemo.tsx`) wraps content in `PulseErrorBoundary` so render-thrown errors call `reportDeviceCrash`.


| Event name               | Trigger                                              |
| ------------------------ | ---------------------------------------------------- |
| `shop_now_click`         | Home page "Shop Now" button                          |
| `add_to_cart`            | Product card + product detail page                   |
| `cart_remove_item`       | Cart page remove button                              |
| `cart_checkout_click`    | Cart page checkout link (with `item_count`, `total`) |
| `error_report_exception` | Error demo: reportException trigger                  |
| `error_track_nonfatal`   | Error demo: trackNonFatal trigger                    |


---

## What Has Been Tested

### Unit tests (`src/__tests__/`) — **123** tests (`m1.test.ts`, `export-sampling-gate.test.ts`, `merge-pulse-sdk-config.test.ts`, `sampling-signal-match.test.ts`)

Suites include (non-exhaustive): **Installation ID**, **Session Provider**, **Config validation**, **Resource builder**, **SDK singleton / consent**, **SdkConfigFetcher**, **mergePulseSdkConfig** / critical-policy normalization, **FeatureGate**, **ExportSamplingGate** (session rate 0, critical bypass, `signalsToSample`), **`pulseSignalConditionMatches`** (invalid-regex fallback), **GlobalAttributesProcessor**, **SessionInstrumentation**, **SDK public API**, **SignalFilterProcessor**, and more.

**Note:** `m1.test.ts` mocks `../exporters`; deep transport behavior is covered mainly by E2E and manual checks.

Run: `cd pulse-web-otel && yarn test:run`.

### E2E tests (`examples/ecommerce-demo/e2e/m1.spec.ts`) — **63** tests

Playwright config: `examples/ecommerce-demo/e2e/playwright.config.ts` (pass `--config e2e/playwright.config.ts` from the demo package). Vite `--mode test` uses `.env.test` (`VITE_PULSE_ENDPOINT_BASE_URL`, `VITE_PULSE_BATCH_DELAY_MS=200`, `VITE_PULSE_FORMAT=json`, `VITE_PULSE_COMPRESSION=none`). OTLP routes + CORS fixtures in `e2e/fixture`.

Filter M1-focused groups: `yarn e2e --grep "@M1"` from `examples/ecommerce-demo/` (see file header).


| Suite                          | Tests                                                                                                                           |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| **@M1 session lifecycle**      | `session.start` on page load; `session.end` on pagehide; BFCache no session.end; double `start()` = exactly one `session.start` |
| **@M1 identity persistence**   | `installation.id` survives reload; localStorage; fallback when `localStorage` throws; new `session.id` per fresh load           |
| **@M1 OTLP pipeline**          | `x-api-key`; `Content-Type: application/json` when `.env.test` sets `VITE_PULSE_FORMAT=json`; resource attributes                |
| **@M1 SDK shutdown**           | `shutdown()` force-flushes without errors                                                                                       |
| **@M1 batching**               | Multiple `trackEvent` coalesced; first export after batch delay; pagehide flush; `session.end` ordering                         |
| **@M1 payload attributes**     | `session.start` contract attrs; global attrs; resource attrs; `sdk.init` span                                                   |
| **@M1 localStorage state**     | `pulse_installation_id`; match to signal; `pulse_sdk_config` after fetch                                                        |
| **@M1 consent**                | DENIED → not initialized; zero OTLP                                                                                             |
| **@M1 signal headers**         | Metering header present; **stability** uses timed waits so two `/v1/logs` exports occur before asserting one UUID               |
| **@M1 app.installation.start** | First visit vs reload                                                                                                           |
| **@M1 trackNonFatal**          | Attributes + body                                                                                                               |
| **@M1 reportException body**   | Body = error message                                                                                                            |


---

## Manual Testing Steps

### Prerequisites

1. Start the ingest stack:
  ```bash
   cd deploy && ./scripts/start.sh -d
  ```
2. Start the demo app:
  ```bash
   cd pulse-web-otel && yarn demo
   # → http://localhost:3002
  ```

### Test 1 — SDK initialises correctly

1. Open `http://localhost:3002` in Chrome DevTools → Network tab
2. Filter by `v1/logs`
3. Verify a POST to `localhost:4318/v1/logs` fires within 1–2 seconds
4. In the request body (JSON mode): confirm `pulse.type = "session.start"` with `session.id`, `installation.id`, `platform = "web"`

### Test 2 — trackEvent fires on CTA clicks

1. On the Home page, click **"Shop Now →"**
2. In DevTools Network → `v1/logs`, find a log with `body.stringValue = "shop_now_click"` and `pulse.type = "custom_event"`
3. Click **Add to Cart** on any product card — look for `body = "add_to_cart"` with `product_id`, `product_name`, `price`
4. Navigate to Cart, click **Remove** → `cart_remove_item`; click **Checkout** → `cart_checkout_click` with `item_count`, `total`

### Test 3 — reportException and trackNonFatal

1. Navigate to `/error-demo`
2. Click "Trigger reportException" → look for `pulse.type = "non_fatal"`, body = the error message, `non_fatal.is_manual = true`
3. Click "Trigger trackNonFatal" → same `pulse.type`, body = event name, `non_fatal.type` set

### Test 4 — Protobuf vs JSON format

1. **Default (production):** unset `VITE_PULSE_FORMAT` (or set `protobuf`) — `v1/logs` POST uses **`Content-Type: application/x-protobuf`** and binary body.
2. For readable bodies in DevTools, set `VITE_PULSE_FORMAT=json` in `examples/ecommerce-demo/.env.local`.
3. Optionally set `VITE_PULSE_COMPRESSION=gzip` and confirm `Content-Encoding: gzip` when the collector allows it. E2E uses **`json` + `none`** (`.env.test`) so Playwright fixtures can decode payloads.

### Test 5 — X-Pulse-Metering-Session-ID header

1. In DevTools → Network → any `v1/logs` or `v1/traces` request → Headers tab
2. Confirm `x-pulse-metering-session-id` is present and is a UUID
3. Reload the page — the UUID should be different (new SDK init = new metering session)
4. On the same page, compare multiple OTLP requests — the UUID must be identical across all requests within a page load

### Test 6 — Installation ID persistence

1. Open DevTools → Application → Local Storage → `localhost:3002`
2. Confirm `pulse_installation_id` key exists with a UUID value
3. Hard-reload the page (Cmd+Shift+R) — `pulse_installation_id` must be the same UUID
4. Open a new tab to the same URL — same UUID
5. Delete `pulse_installation_id` from Local Storage, reload — a new UUID is created and `pulse.app.installation.start` log is emitted

### Test 7 — Session end on page close

1. Open the demo, wait for `session.start` in the Network tab
2. Close the tab or navigate away
3. In ClickHouse (or the Network tab before close): verify `session.end` log arrives with `session.duration_ms`

### Test 8 — Consent gate

1. Navigate to `http://localhost:3002/?pulse_consent=denied`
2. Check DevTools → Network: zero calls to `v1/logs`, `v1/traces`, or `v1/metrics`
3. Open Console: `window.PulseWeb.isInitialized()` → `false`

### Test 9 — First-install detection

1. Open DevTools → Application → Local Storage — delete `pulse_installation_id`
2. Hard-reload
3. In `v1/logs` network requests: find `body = "pulse.app.installation.start"` with `pulse.type = "pulse.app.installation.start"` and `installation.id`
4. Reload again — this log must NOT appear a second time

---

## Running Tests

### Unit tests (Vitest)

```bash
cd pulse-web-otel
yarn test:run          # single run — all src/__tests__/**/*.test.ts
yarn test              # watch mode
```

### E2E tests (Playwright)

```bash
# Demo dev server is not required — webServer in playwright config starts Vite on :3099
cd pulse-web-otel
yarn demo:test         # workspace: e2e + HTML report under examples/ecommerce-demo/e2e-report/
```

The `demo:test` script runs `yarn workspace ecommerce-demo e2e:report`, which executes `playwright test --config e2e/playwright.config.ts` from `examples/ecommerce-demo/` and opens the HTML report.

Run a specific suite (always pass config from the demo package root):

```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn e2e --grep "@M1 signal headers"
# or: yarn playwright test --config e2e/playwright.config.ts e2e/m1.spec.ts --project=chromium
```

Run headed (watch browser):

```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn e2e:headed
```

### Type check

```bash
cd pulse-web-otel
yarn lint              # tsc --noEmit
```

---

## Internal code review — strengths, gaps, verification

Structured pass over `src/` (init, session, exporters, persistence, processors, remote config, registry, session instrumentation). **No P0** called from static review alone; below is what to fix or verify next.

### Strengths

- Init order in `sdk.ts` is explicit and easy to extend (identity → resource → cached config → processors → providers → registry → background fetch → `sdk.init` + optional installation log).
- `SessionProvider` models real browsers: inactivity, max lifetime, page-hidden timeout, BFCache, clone/reuse, `session.end` reasons.
- Browser-specific OTLP: custom exporters + transformer (not Node HTTP clients), retry + optional IndexedDB persist + gzip, documented lazy transport init.
- `PulseGlobalAttributesProcessor` avoids clobbering `session.id` on logs when already set by session instrumentation (correct `session.start` / `session.end` contract).
- Metrics: `GlobalAttributeInjectingMetricExporter` merges the same dynamic attrs into metric points as traces/logs.

### Gaps and risks (prioritized)

| Priority | Topic | Detail |
|----------|--------|--------|
| ~~P1~~ | Default OTLP format | **Resolved:** omitted `export.format` ⇒ protobuf (`src/exporters.ts`). |
| ~~P1~~ | Sampling vs Android | **Resolved:** `ExportSamplingGate` + sampled exporters mirror Android export-time filter (`sessionSampleRate: 0` ⇒ no OTLP). |
| P1 | Remote `session` feature off | Disables **all** session instrumentation including `session.start` / `session.end`. Confirm this is intended when `session` feature is disabled in remote config. |
| P1 | `getPreviousSessionId()` | Reads `localStorage['pulse_prev_session_id']` but **nothing in-repo writes that key** — `session.previousSessionId` on `session.start` is usually empty unless extended later. |
| P2 | Keepalive log export success | `KeepaliveFetchLogExporter` treats fulfilled `fetch` as success; **non-2xx** may still dequeue — verify retry expectations. |
| P2 | Remote config logging | `sdkConfigDevLog` in `remote-config.ts` uses **`console.log`** — noisy in production; consider gating on `debug` / dev. |
| P2 | `SignalFilterProcessor` traces | Trace attribute mutation not implemented; only log path fully wired. |
| P2 | Unit vs integration coverage | `m1.test.ts` **mocks** `../exporters` — batch delays, pagehide keepalive path, protobuf headers, metric injection: rely on E2E or add focused exporter tests. |

### Privacy / security notes

- OTLP bodies in IndexedDB (`diskBuffering`) can hold URLs, stacks, and custom attrs — treat as sensitive.
- `page.url` uses `location.href` (query strings / tokens). Stacks and `trackEvent` bodies can carry PII — same as other RUM SDKs; document for integrators.
- Consent: `DENIED` and **`PENDING`** both block `start()` (strict). Confirm product intent for `PENDING`.

### Verification checklist (engineering)

1. E2E: confirm `sessionSampleRate: 0` yields zero OTLP intercepts (sampling is client-side).
2. Intended `session.start` behavior on reload when session is reused.
3. OTel SDK version: does `setAttribute(key, undefined)` actually drop keys in `SignalFilterProcessor`?

---

## Known limitations / TODOs


| Item                                   | Status        | Notes                                                                                                                                     |
| -------------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Browser gzip                           | **Done (M1)** | `src/utils/otlp-gzip.ts` + transport wrapper; use `export.compression: 'none'` when `CompressionStream` is missing or for tests        |
| `beforeSend` hook                      | **Not wired** | Declared on `PulseWebConfig`; no processor/exporter calls it yet (M3 plan)                                                               |
| Default protobuf on wire              | **Done**      | Omitted `export.format` uses protobuf; `export.format: 'json'` for dev/tests                                                           |
| Sampling vs Android                   | **Done**      | `ExportSamplingGate` + sampled span/log/metric exporters + keepalive log filter                                                        |
| `pulse_prev_session_id` persistence   | **Unused**    | Reader exists; writer not implemented — previous session id on logs stays empty unless added later                                       |
| Web Vitals / clicks / network / nav    | M3            | Only `src/instrumentations/session.ts` exists today; other instrumentations are **not** in this package tree yet (per milestones)       |
| React `PulseProvider` / SSR guard       | M2            | Not in package; `PulseErrorBoundary` + `@dreamhorizon/pulse-web/react` only                                                              |
| Next.js integration                    | Later         | Planned                                                                                                                                   |
| CDN/UMD bundle                         | M5            | Planned                                                                                                                                   |


