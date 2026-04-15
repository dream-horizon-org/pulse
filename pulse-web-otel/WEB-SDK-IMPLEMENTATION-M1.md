# Pulse Web SDK — M1 Implementation

Package: `@dreamhorizon/pulse-web`  
Status: **M1 Complete — 32/32 unit tests + 35 E2E tests**

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
  │    url.path, platform='web', browser.name, os.name, etc.)           │
  │          │                                                           │
  │          ▼                                                           │
  │  PulseSamplingProcessor (applies remote config sample rate)          │
  │          │                                                           │
  │          ▼                                                           │
  │  SignalFilterProcessor (gates per signal type from remote config)    │
  │          │                                                           │
  │          ▼                                                           │
  │  BatchSpanProcessor / BatchLogRecordProcessor                        │
  │  (delay: 5s default / 200ms test mode; queue: 2048; batch: 512)     │
  │          │                                                           │
  │          ▼                                                           │
  │  OTLP Exporter (protobuf default / JSON dev mode)                   │
  │  Headers: X-API-KEY + X-Pulse-Metering-Session-ID                   │
  │          │                                                           │
  │          ▼                                                           │
  │  OTEL Collector :4318/v1/{traces|logs|metrics}                      │
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

| Feature | Details |
|---|---|
| Singleton guard | `PulseWebSDK.getInstance()` — `start()` is a no-op if already initialized. Handles React StrictMode double-invoke. |
| Config validation | `validateConfig()` throws on missing `endpointBaseUrl`, `apiKey`, `serviceName` |
| Consent gate | `PulseDataCollectionConsent.DENIED` or `PENDING` → zero signals, zero network calls |
| 10-step init sequence | Validate → session → install ID → resource → remote config → feature gate → processors → providers → instruments → background fetch |
| Metering session ID | `crypto.randomUUID()` — stable UUID per page load, sent as `X-Pulse-Metering-Session-ID` on every OTLP request (mirrors Android) |
| `sdk.init` heartbeat | Span emitted on every init, `pulse.type=sdk.init`, `platform=web` |
| `pulse.app.installation.start` | Log emitted on first-ever install only — detected via `wasNewInstallation()` |
| `shutdown()` | Force-flushes all providers, unregisters instrumentations, clears session |

### 2. Public API (`src/sdk.ts`)

| Method | Signal type | Body | Key attributes |
|---|---|---|---|
| `trackEvent(name, attrs?)` | Log | event name | `pulse.type=custom_event`, `event.name=pulse.custom_event`, custom attrs |
| `reportException(error, attrs?)` | Log | `error.message` | `pulse.type=non_fatal`, `exception.type`, `exception.message`, `exception.stacktrace`, `non_fatal.is_manual=true` |
| `trackNonFatal(name, attrs?)` | Log | event name | `pulse.type=non_fatal`, `non_fatal.type`, `non_fatal.is_manual=true`, custom attrs |
| `setScreenName(name)` | — | sets global attr `screen.name` on all subsequent signals |
| `isInitialized()` | — | returns boolean |

> **Android parity note:** `trackEvent` emits a **log** (not a span) matching Android SDK behaviour. `reportException` body is the error message (not the literal string `'non_fatal'`). `trackNonFatal` is a named non-fatal variant without a stack trace.

### 3. Session Management (`src/session.ts`)

| Feature | Details |
|---|---|
| Session ID | UUID v4, regenerated on inactivity timeout (default: 30 min) |
| Inactivity timeout | Configurable via `config.instrumentations.session.inactivityTimeoutMs` |
| BFCache guard | `pagehide` with `persisted=true` → updates activity timestamp, does NOT end session |
| `session.start` log | Emitted on SDK init with `session.id`, `installation.id`, `platform=web` |
| `session.end` log | Emitted on `pagehide` (non-BFCache) with `session.duration_ms`, `screens_visited` |
| Session rotation | `onSessionChange` callback, emits `session.end` + new `session.start` on rotation |

### 4. Installation ID (`src/session.ts`)

3-tier persistence — silently falls back when storage is unavailable (e.g., Safari ITP, privacy mode):

| Tier | Storage | Key |
|---|---|---|
| 1 (preferred) | `localStorage` | `pulse_installation_id` |
| 2 (fallback) | `sessionStorage` | `pulse_installation_id` |
| 3 (last resort) | Module-level memory | — |

`wasNewInstallation()` returns `true` only when the ID was created fresh (not read from storage). Used to gate the `pulse.app.installation.start` log — emitted once per device, never on reload.

### 5. OTLP Wire Format (`src/exporters.ts`)

| Setting | Default | Dev mode |
|---|---|---|
| Format | `protobuf` (`application/x-protobuf`) | `json` (`application/json`) |
| Compression | `gzip` (TODO: browser-native) | `none` |
| Auth header | `X-API-KEY: <apiKey>` | same |
| Metering header | `X-Pulse-Metering-Session-ID: <uuid>` | same |

Both proto (`@opentelemetry/exporter-*-otlp-proto`) and JSON (`@opentelemetry/exporter-*-otlp-http`) exporters are bundled. Format is selected at init time via `config.export.format`.

**Batch settings (default):**
- `scheduledDelayMillis`: 5000 ms
- `maxQueueSize`: 2048
- `maxExportBatchSize`: 512
- `exportTimeoutMillis`: 30000 ms

Pagehide handler calls `forceFlush()` on all 3 providers before the batch timer fires.

### 6. Remote Config (`src/remote-config.ts`)

| Feature | Details |
|---|---|
| Config URL auto-derivation | `:4318` collector URL → `:8080` server URL automatically |
| Cache | Stored in `localStorage` as `pulse_sdk_config` (JSON) |
| Version-gated writes | Only writes to localStorage when fetched version > cached version |
| Background fetch | `fetchInBackground()` — fire-and-forget, does not block init |
| Default config | `DEFAULT_SDK_CONFIG` used when no cached config and fetch fails |

### 7. Feature Gate (`src/feature-gate.ts`)

Remote config `features[]` array controls per-signal enable/disable by `featureName` + `sessionSampleRate` per SDK (`pulse_web_js`). Defaults to enabled when the feature is absent from the config.

### 8. Processors

| Processor | Role |
|---|---|
| `GlobalAttributesProcessor` | Injects `session.id`, `installation.id`, `screen.name`, `url.path`, `page.url`, `browser.name`, `browser.version`, `os.name`, `os.version`, `device.type`, `network.connection.type`, `rum.sdk.version`, `project.id`, `platform=web` on every span + log |
| `PulseSamplingProcessor` | Drops signals per `sessionSampleRate` from remote config |
| `SignalFilterProcessor` | Drops entire signal categories (e.g. disable all `non_fatal` via remote config) |

### 9. OTEL Collector Config (`backend/ingestion/otel-collector.yaml`)

`X-Pulse-Metering-Session-ID` added to CORS `allowed_headers` — required for browser cross-origin OTLP requests. Without this the browser silently drops all OTLP exports.

### 10. Ecommerce Demo Instrumentation

`trackEvent` calls wired to every CTA in the demo app:

| Event name | Trigger |
|---|---|
| `shop_now_click` | Home page "Shop Now" button |
| `add_to_cart` | Product card + product detail page |
| `cart_remove_item` | Cart page remove button |
| `cart_checkout_click` | Cart page checkout link (with `item_count`, `total`) |
| `error_report_exception` | Error demo: reportException trigger |
| `error_track_nonfatal` | Error demo: trackNonFatal trigger |

---

## What Has Been Tested

### Unit Tests (`src/__tests__/m1.test.ts`) — 32 tests

| Suite | Tests |
|---|---|
| **M1 — Installation ID** | Creates + persists in localStorage; falls back to sessionStorage; falls back to memory; returns same ID on repeated calls |
| **M1 — Session Provider** | Valid UUID v4; rotates after inactivity; sets `previousSessionId` on rotation; BFCache `pagehide` (persisted=true) does NOT emit session.end |
| **M1 — Config validation** | Throws on missing `endpointBaseUrl`, `apiKey`, `serviceName`; passes with all fields |
| **M1 — Resource builder** | `platform=web`; `rum.sdk.name=pulse_web_js`; `service.name` from config; `project.id` extracted from apiKey |
| **M1 — SDK singleton guard** | Second `start()` is no-op; `shutdown()` allows re-initialization |
| **M1 — resolveConfigUrl** | Replaces `:4318` with `:8080`; uses explicit URL as-is; non-4318 URLs unchanged |
| **M1 — SdkConfigFetcher** | Loads cached config; persists when version changes; skips write when version same |
| **M1 — FeatureGate** | Returns `true` for absent features; returns `false` for `sessionSampleRate=0` |
| **M1 — wasNewInstallation** | Returns `true` on fresh install; returns `false` on reload; returns `false` with sessionStorage ID |
| **M1 — SDK public API signals** | `reportException` body = error message; `trackNonFatal` body + attributes; `trackEvent` emits log (not span) |

### E2E Tests (`examples/ecommerce-demo/e2e/m1.spec.ts`) — 35 tests

| Suite | Tests |
|---|---|
| **@M1 session lifecycle** | `session.start` on page load; `session.end` on pagehide; BFCache no session.end; double start() = exactly one session.start |
| **@M1 identity persistence** | `installation.id` survives reload; stored in localStorage; falls back when localStorage throws; new session.id on each fresh load |
| **@M1 OTLP pipeline** | `x-api-key` header; Content-Type is `application/json` (dev mode); resource attributes present |
| **@M1 SDK shutdown** | `shutdown()` force-flushes without errors |
| **@M1 batching** | 3 trackEvent calls coalesced into one payload; first export after batch delay; pagehide force-flushes before timer; session.end before pagehide batch window |
| **@M1 payload attributes** | `session.start` required data-contract attributes; global attributes on every signal; resource attributes correct; `sdk.init` heartbeat span |
| **@M1 localStorage state** | `pulse_installation_id` is UUID; matches value in session.start signal; `pulse_sdk_config` valid JSON after fetch |
| **@M1 consent** | DENIED → `isInitialized()=false`; DENIED → zero OTLP calls |
| **@M1 signal headers** | `X-Pulse-Metering-Session-ID` sent on every request; stable across multiple requests in same session |
| **@M1 app.installation.start** | Emitted on first visit; NOT emitted on reload |
| **@M1 trackNonFatal** | Correct attributes + body |
| **@M1 reportException body** | Body = error message |

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

1. Open `.env.local` in `examples/ecommerce-demo/`. Confirm `VITE_PULSE_FORMAT=json` for dev mode.
2. In DevTools → Network → click a `v1/logs` request → Preview should show readable JSON (not binary).
3. To test protobuf: set `VITE_PULSE_FORMAT=protobuf` (or remove the env var), reload — the request body will appear as binary in the Preview tab. The OTEL Collector will still accept it.

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
yarn test:run          # single run — 32 tests
yarn test              # watch mode
```

### E2E tests (Playwright)

```bash
# Prerequisites: demo app dev server is NOT needed — Playwright spawns it via webServer config
cd pulse-web-otel
yarn demo:test         # run all tests, then open HTML report in browser
```

The `demo:test` script:
1. Runs `playwright test --config e2e/playwright.config.ts`
2. Opens the HTML report in your default browser via `playwright show-report e2e-report`

Run a specific suite:
```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn e2e --grep "@M1 signal headers"
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

## Known Limitations / TODOs

| Item | Status | Notes |
|---|---|---|
| Browser gzip compression | TODO | `CompressionAlgorithm` is Node-only in `@opentelemetry/otlp-exporter-base` 0.53. Requires custom XHR/fetch exporter wrapping `CompressionStream`. |
| Web Vitals instrumentation | M2 | `instrumentations/web-vitals.ts` scaffolded, not wired |
| Click tracking (`app.click`) | M2 | `instrumentations/clicks.ts` scaffolded, not wired |
| Network instrumentation (`http` spans) | M2 | `instrumentations/network.ts` scaffolded, not wired |
| React integration (`PulseProvider`) | M3 | Planned |
| Next.js integration | M3 | Planned |
| CDN/UMD bundle | M5 | Planned |
