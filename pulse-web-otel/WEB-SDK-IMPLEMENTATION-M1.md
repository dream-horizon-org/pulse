# Pulse Web SDK — M1 Implementation

Package: `@dreamhorizon/pulse-web`  
Status: **M1 complete — 97 Vitest cases (`m1.test.ts`) + 31 Playwright tests (`e2e/m1.spec.ts`, Chromium)**

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
  │  Step 6: FeatureGate + SamplingProcessor + FilterProcessor      │
  │  Step 7: GlobalAttributesProcessor                              │
  │  Step 8: createProviders() → TracerProvider, LoggerProvider,    │
  │          MeterProvider                                          │
  │  Step 9: Register global providers                              │
  │  Step 10: InstrumentationRegistry.installAll()                  │
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
  │  PulseSamplingProcessor (applies remote config sample rate)          │
  │          │                                                           │
  │          ▼                                                           │
  │  SignalFilterProcessor (gates per signal type from remote config)    │
  │          │                                                           │
  │          ▼                                                           │
  │  BatchSpanProcessor / BatchLogRecordProcessor                        │
  │  (delay: 5s default / 200ms test mode; queue: 2048; batch: 512)      │
  │          │                                                           │
  │          ▼                                                           │
  │  PulseBrowser* exporters (JSON or protobuf via otlp-transformer)     │
  │  Optional gzip (CompressionStream); retry; optional IndexedDB buffer │
  │  Headers: X-API-KEY + X-Pulse-Metering-Session-ID                    │
  │          │                                                           │
  │          ▼                                                           │
  │  OTEL Collector :4318/v1/{traces|logs|metrics}                       │
  │          │                                                           │
  │          ├──▶ ClickHouse (otel_traces, otel_logs, otel_metrics_gauge)│
  │          └──▶ pulse-server :8080 (non_fatal, device.crash logs only) │
  └──────────────────────────────────────────────────────────────────────┘

  Pagehide / unload path:
  window 'pagehide' (persisted=false)
          │
          ▼
  forceFlush() on all 3 providers → drains in-flight batch immediately
  SessionProvider emits session.end (with duration_ms, screens_visited)
```

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
| `sdk.init` heartbeat           | Span emitted on every init, `pulse.type=sdk.init`, `platform=web`                                                                                                                      |
| `pulse.app.installation.start` | Log emitted on first-ever install only — detected via `wasNewInstallation()`                                                                                                           |
| `shutdown()`                   | Force-flushes all providers, unregisters instrumentations, clears session                                                                                                              |


### 2. Public API (`src/sdk.ts`)


| Method                             | Signal type | Body                                                     | Key attributes                                                                                                    |
| ---------------------------------- | ----------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `trackEvent(name, attrs?)`         | Log         | event name                                               | `pulse.type=custom_event`, `event.name=pulse.custom_event`, custom attrs                                          |
| `reportException(error, attrs?)`   | Log         | `error.message`                                          | `pulse.type=non_fatal`, `exception.type`, `exception.message`, `exception.stacktrace`, `non_fatal.is_manual=true` |
| `trackNonFatal(name, attrs?)`      | Log         | event name                                               | `pulse.type=non_fatal`, `non_fatal.type`, `non_fatal.is_manual=true`, custom attrs                                |
| `reportDeviceCrash(error, attrs?)` | Log         | error message                                            | `pulse.type=device.crash`, `exception.*`, `error.filename` (from stack); used by `PulseErrorBoundary`             |
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
| Inactivity timeout  | Configurable via `config.instrumentations.session.inactivityTimeoutMs`              |
| BFCache guard       | `pagehide` with `persisted=true` → updates activity timestamp, does NOT end session |
| `session.start` log | Emitted on SDK init with `session.id`, `installation.id`, `platform=web`            |
| `session.end` log   | Emitted on `pagehide` (non-BFCache) with `session.duration_ms`, `screens_visited`   |
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
| Format          | `json` if omitted                              | `config.export.format`: `json` → `application/json`; `protobuf` → `application/x-protobuf`                                                                         |
| Compression     | On when `config.export.compression !== 'none'` | If `CompressionStream` is missing, gzip wrapper is skipped (same as Node-less browsers). E2E `.env.test` sets `VITE_PULSE_COMPRESSION=none` for plain JSON bodies. |
| Transport       | XHR (`createPulseXhrTransport`)                | Chain: retrying → optional IndexedDB persist → optional gzip → XHR (`buildBrowserExportTransport` in `otlp-transport.ts`)                                          |
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
| `PulseSamplingProcessor`    | Drops signals per `sessionSampleRate` from remote config                                                                                                                                                                                                  |
| `SignalFilterProcessor`     | Drops entire signal categories (e.g. disable all `non_fatal` via remote config)                                                                                                                                                                           |


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

### Unit tests (`src/__tests__/m1.test.ts`) — 97 cases

Suites include (non-exhaustive): **Installation ID** (storage tiers + extended cases), **Session Provider** (rotation, BFCache, extended), **Config validation**, **Resource builder** (extended), **SDK singleton guard**, **resolveConfigUrl**, **SdkConfigFetcher**, **FeatureGate**, **wasNewInstallation**, **computeAspectRatio**, **GlobalAttributesProcessor**, **SessionInstrumentation events**, **SDK public API signals** (`trackEvent` / `reportException` / `trackNonFatal`).

Run: `cd pulse-web-otel && yarn test:run`.

### E2E tests (`examples/ecommerce-demo/e2e/m1.spec.ts`) — 31 tests

Playwright config: `e2e/playwright.config.ts` (must pass `--config` when invoking `playwright test` from a directory that is not `ecommerce-demo/`). Web server uses Vite `--mode test` (`.env.test`: `VITE_PULSE_ENDPOINT_BASE_URL=http://127.0.0.1:4318`, `VITE_PULSE_BATCH_DELAY_MS=200`, `VITE_PULSE_FORMAT=json`, `VITE_PULSE_COMPRESSION=none`). Routes intercept OTLP; CORS preflight is fulfilled in fixtures.


| Suite                          | Tests                                                                                                                           |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| **@M1 session lifecycle**      | `session.start` on page load; `session.end` on pagehide; BFCache no session.end; double `start()` = exactly one `session.start` |
| **@M1 identity persistence**   | `installation.id` survives reload; localStorage; fallback when `localStorage` throws; new `session.id` per fresh load           |
| **@M1 OTLP pipeline**          | `x-api-key`; `Content-Type: application/json` (test mode); resource attributes                                                  |
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

1. Open `.env.local` in `examples/ecommerce-demo/`. Set `VITE_PULSE_FORMAT=json` for readable bodies in DevTools.
2. In DevTools → Network → a `v1/logs` POST → Preview should show JSON when format is `json`.
3. Set `VITE_PULSE_FORMAT=protobuf` and reload — body is protobuf bytes (`Content-Type: application/x-protobuf`). Optionally set `VITE_PULSE_COMPRESSION=gzip` (browser `CompressionStream`) and confirm `Content-Encoding: gzip` when the collector allows it. E2E uses `json` + `none` so Playwright can decode payloads.

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
yarn test:run          # single run — 97 cases in m1.test.ts
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

## Known limitations / TODOs


| Item                                   | Status        | Notes                                                                                                                                     |
| -------------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Browser gzip                           | **Done (M1)** | `src/utils/otlp-gzip.ts` + transport wrapper; disabled with `export.compression: 'none'` when `CompressionStream` is missing or for tests |
| Web Vitals instrumentation             | M2            | `instrumentations/web-vitals.ts` scaffolded, not wired                                                                                    |
| Click tracking (`app.click`)           | M2            | `instrumentations/clicks.ts` scaffolded, not wired                                                                                        |
| Network instrumentation (`http` spans) | M2            | `instrumentations/network.ts` scaffolded, not wired                                                                                       |
| React `PulseProvider` / context        | M3            | Planned — M1 ships `PulseErrorBoundary` + `@dreamhorizon/pulse-web/react` only                                                            |
| Next.js integration                    | M3            | Planned                                                                                                                                   |
| CDN/UMD bundle                         | M5            | Planned                                                                                                                                   |


