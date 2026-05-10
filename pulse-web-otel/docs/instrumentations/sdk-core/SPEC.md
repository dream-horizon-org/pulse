# SDK Core — SPEC.md

Package: `@dreamhorizon/pulse-web`
File: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md`

---

## 1. Goal

Define the non-instrumentation core of the Pulse Web SDK: initialization lifecycle, configuration contract, consent gate, feature gates, remote config fetch/cache, OTLP exporters, export sampling, IndexedDB persistence, session management, and the public API surface exposed via `src/index.ts`.

This document is the shared data contract reference for all instrumentation SPEC files (issues 02–07). Every signal emitted by an instrumentation must conform to the attribute table in §5.

---

## 2. Assumptions

- The SDK runs in browser environments only. `window` / `globalThis.window` must be defined; SSR / Node environments receive a no-op init.
- The host app supplies `apiKey` and `dataCollectionState` at init time. Both are required.
- All instrumentations run on the browser main thread. No Web Worker support in v0.1.
- `localStorage` is available for session/user persistence and SDK config cache. A quota of ~5 MB is assumed; the persistence module truncates oldest-first on overflow.
- OTel SDK (traces, logs, metrics) is bundled with the SDK — not expected as a peer dep from the host.
- Android and React Native SDKs share the same `pulse.type` semantic convention; web must not diverge.
- Remote config is fetched in the background after init; the locally cached version from the previous session is used immediately to gate instrumentations.

---

## 3. Requirements

### Functional

**R1 — Init:** `Pulse.init(config)` must be idempotent (double-call is a no-op). Returns a `Promise<void>` that resolves when async bootstrap (OS version resolution, provider wiring) completes.

**R2 — Consent gate:** `dataCollectionState !== ALLOWED` → no signals emitted, no listeners installed. The SDK must be callable (`Pulse.init`) even when consent is `PENDING` or `DENIED`; it simply exits early with no side effects.

**R3 — Feature gate:** Every instrumentation checks `FeatureGate.isEnabled(feature)` before installing event listeners. A remote config can reduce `sessionSampleRate` to 0 to disable a feature without re-deploying.

**R4 — Remote config:** `SdkConfigFetcher.loadCached()` reads `localStorage["pulse_sdk_config"]` synchronously at init. `fetchInBackground()` fires a `fetch` call post-init and persists a new version only if the remote version number differs.

**R5 — Shutdown:** `Pulse.shutdown()` must uninstall all instrumentations, remove the `pagehide` listener, force-flush all providers, and reset the singleton so a subsequent `Pulse.init()` re-bootstraps cleanly.

**R6 — Session:** `SessionProvider` assigns a `session.id` UUID on construction. It rotates the session after `pageHiddenTimeoutMs` of backgrounding (default 30 min). Sessions persist `installationId` and `userId` to `localStorage`.

**R7 — Public API:** All methods on `Pulse` must silently no-op when called before `init` completes or after `shutdown`.

**R8 — platform=web mandate:** Every signal emitted by the SDK must carry `platform = 'web'` as an OTel Resource attribute (`os.name = 'web'`). This is set once in `buildMergedResource()` and is not overridable by the host app.

**R9 — Export sampling:** `ExportSamplingGate` evaluates session-level sampling rules at export time (not span-creation time), preserving parent/child span sampling consistency.

**R10 — IndexedDB drain:** On init, if `diskBuffering.enabled !== false`, the SDK replays any buffered OTLP batches from IndexedDB that were written by a previous session that crashed before flushing.

### Non-functional

- **Bundle size:** gated by `size-limit` in CI. No lodash, moment, or Node-only deps.
- **Logging:** All internal logs route through `PulseWebLogger`; consumers can silence via `logLevel: PulseLogLevel.NONE`.
- **Thread safety:** Init is re-entrant safe via `_initializing` guard. Concurrent `init()` calls during async bootstrap return the same in-flight promise.

---

## 4. Architectural Design

```
Host App
  │
  ▼
Pulse.init(config)          ← singleton facade (src/sdk.ts)
  │
  ├─ validateConfig()        ← src/config.ts
  ├─ isDataCollectionAllowed()  ← src/consent.ts
  ├─ SessionProvider         ← src/session.ts
  ├─ UA parse + OS version   ← src/utils/ua-parser.ts  (async <200ms)
  ├─ buildMergedResource()   ← src/resource.ts  (platform=web stamped here)
  │
  ├─ SdkConfigFetcher.loadCached()  ← localStorage → PulseSdkConfig
  ├─ FeatureGate(sdkConfig)         ← src/feature-gate.ts
  ├─ ExportSamplingGate(sdkConfig)  ← src/sampling/export-sampling-gate.ts
  ├─ PulseGlobalAttributesProcessor ← src/processors/global-attrs-processor.ts
  ├─ SignalFilterProcessor           ← src/processors/signal-filter-processor.ts
  │
  ├─ createProviders(exporterConfig, resource, processors)  ← src/exporters.ts
  │     └─ WebTracerProvider + LoggerProvider + MeterProvider
  │
  ├─ drainBufferedOtlpExports()  ← src/persistence/drain-buffered-exports.ts
  ├─ bindPagehideFlush()         ← pagehide → forceFlush all providers
  ├─ bindGlobalProviders()       ← OTel global provider registration
  │
  ├─ InstrumentationRegistry.installAll()  ← src/instrumentation-registry.ts
  │     ├─ SessionInstrumentation
  │     ├─ ClicksInstrumentation
  │     ├─ WebVitalsInstrumentation
  │     ├─ NetworkInstrumentation
  │     ├─ NavigationInstrumentation
  │     └─ ErrorInstrumentation
  │
  ├─ InteractionInstrumentation (registered separately)
  │
  └─ SdkConfigFetcher.fetchInBackground()  (async, post-init)
```

**Singleton pattern:** `PulseSDK._instance` is a module-level singleton. `Pulse` is exported as `PulseSDK.getInstance()`. There is no factory or class constructor exposed publicly.

**Consent-first design:** The consent check runs before any session or resource construction. GDPR-safe: zero allocations, zero signals when `dataCollectionState !== ALLOWED`.

**Async init race guard:** `_initializing` is set synchronously before the `finishInit` async chain begins. Any concurrent `init()` call during the 200ms OS-version await returns the same in-flight promise rather than double-constructing providers.

---

## 5. LLD

### 5.1 `pulse.type` Enum

All `pulse.type` values are defined in `PulseWebSemconv.PulseType` (`src/semconv.ts`). The complete enum:

| pulse.type | Signal | Emitter | Notes |
|---|---|---|---|
| `session.start` | Log | `SessionInstrumentation` | New `session.id` assigned |
| `session.end` | Log | `SessionInstrumentation` | Background timeout or explicit shutdown |
| `device.crash` | Log | `ErrorInstrumentation`, `PulseErrorBoundary` | `severityNumber = FATAL` |
| `non_fatal` | Log | `ErrorInstrumentation`, `Pulse.reportException`, `Pulse.trackNonFatal` | `severityNumber = WARN` |
| `http` | Span | `NetworkInstrumentation` | Fetch + XHR |
| `app.click` | Span | `ClicksInstrumentation` | DOM click events |
| `web_vital` | Log | `WebVitalsInstrumentation` | LCP, CLS, FID, INP, FCP, TTFB |
| `screen_load` | Span | `NavigationInstrumentation` | Route entry; carries `tti` |
| `screen_session` | Log | `NavigationInstrumentation` | Route exit/session scoped to screen |
| `custom_event` | Log | `Pulse.trackEvent` | Host-app custom events |
| `app.installation.start` | Log | `PulseSDK.emitInstallationStartIfNeeded` | First-ever install only |
| `pulse.user.session.start` | Log | `PulseSDK.setUserId` | User identity transition |
| `pulse.user.session.end` | Log | `PulseSDK.setUserId` | User identity transition |

**`platform = 'web'` mandate:** The OTel Resource attribute `os.name` is hard-coded to `'web'` in `buildMergedResource()`. Every signal inherits this via the resource — it is not a per-signal attribute and cannot be overridden by host config.

### 5.2 Shared Attribute Table

Every signal emitted by the SDK carries the following attributes. Instrumentations may add signal-specific attributes on top; they must not conflict with these reserved keys.

| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| `pulse.type` | `string` | `PulseWebSemconv.AttributeKey.PULSE_TYPE` | Yes | See §5.1 enum |
| `session.id` | `string` | `PulseGlobalAttributesProcessor` | Yes | UUID per session rotation |
| `user.id` | `string \| null` | `PulseGlobalAttributesProcessor` | No | Persisted in `localStorage` |
| `screen.name` | `string` | `PulseGlobalAttributesProcessor` | No | Set via `Pulse.setScreenName()` |
| `last.screen.name` | `string` | `PulseGlobalAttributesProcessor` | No | Previous screen before transition |
| `installation.id` | `string` | `SessionProvider` / `getOrCreateInstallationId()` | Yes | Stable UUID per browser install |
| `metering.session.id` | `string` | `PulseSDK.init()` / `PulseGlobalAttributesProcessor` | Yes | UUID per SDK init; for billing |
| `pulse.user.<name>` | `string` | `PulseGlobalAttributesProcessor` | No | Custom user properties |
| `os.name` | `string` | OTel Resource (`buildMergedResource`) | Yes | Always `'web'` |
| `os.version` | `string` | OTel Resource (`getOsVersionAsync`) | No | Browser UA / Client Hints |
| `browser.name` | `string` | OTel Resource | No | UA-parsed browser name |
| `app.build_name` | `string` | OTel Resource / config `serviceVersion` | No | App version string |
| `service.name` | `string` | OTel Resource / config `serviceName` | Yes | Identifies the app |
| `project.id` | `string` | OTel Resource / `extractProjectId(apiKey)` | Yes | Extracted from API key prefix |

### 5.3 Config Surface (`PulseWebConfig`)

```ts
{
  apiKey: string;                        // required — "projectId_secret"
  dataCollectionState: PulseDataCollectionConsent; // required — ALLOWED | DENIED | PENDING
  serviceName?: string;                  // defaults to empty string
  serviceVersion?: string;              // app build version
  globalAttributes?: Record<string, string>;
  resourceAttributes?: Record<string, string>;
  instrumentations?: InstrumentationConfig;
  export?: { format: "protobuf" | "json" };
  logLevel?: PulseLogLevel;
  diskBuffering?: PulseWebDiskBufferingConfig;
  beaconRelayUrl?: string;
  beforeSendData?: PulseWebBeforeSendConfig;
  endpoint?: string;                    // internal override; not in public docs
  pageHiddenTimeoutMs?: number;         // session rotation timeout
}
```

`PulseDataCollectionConsent` enum: `ALLOWED`, `DENIED`, `PENDING`.

### 5.4 SDK Init Flow (sequence)

```
Pulse.init(config)
  1. Guard: already initialized or shutting down → return Promise.resolve()
  2. Guard: currently initializing → return this.whenReady()
  3. validateConfig(config) — throws synchronously on invalid apiKey / beforeSendData shape
  4. PulseWebLogger.setLevel(config.logLevel)
  5. resolveEndpointBaseUrl(apiKey, config.endpoint) → endpointBaseUrl
  6. CONSENT GATE: isDataCollectionAllowed(config.dataCollectionState) → if false, return Promise.resolve() (zero side effects)
  7. Set _initializing = true; begin finishInit() async chain:
     a. abortInitIfUnavailable() — SSR guard (window === undefined → abort)
     b. SessionProvider construction + getOrCreateInstallationId()
     c. parseUserAgent() + getOsVersionAsync() — async, <200ms, uses Client Hints if available
     d. buildMergedResource(config, resolvedOsVersion) — OTel Resource with os.name='web'
     e. SdkConfigFetcher.loadCached() — read localStorage["pulse_sdk_config"] → PulseSdkConfig
     f. FeatureGate(sdkConfig), ExportSamplingGate(sdkConfig), PulseGlobalAttributesProcessor
     g. hydrateUserIdentity(persistedUserId, persistedUserProperties)
     h. createProviders(exporterConfig, resource, processors) — builds WebTracerProvider, LoggerProvider, MeterProvider with OTLP exporters
     i. drainBufferedOtlpExports() — replay any IDB-buffered batches from crashed sessions
     j. bindPagehideFlush() — window.addEventListener("pagehide", ...)
     k. bindGlobalProviders() — OTel global trace/logs/metrics registration
     l. emitSdkInitializationLogRecords() — rum.sdk.init.started, rum.sdk.init.span_exporter
     m. InstrumentationRegistry.installAll() — per-feature gate checked per instrumentation
     n. InteractionInstrumentation registration
     o. SdkConfigFetcher.fetchInBackground() — fire-and-forget
     p. _initialized = true; _initializing = false
     q. emitInstallationStartIfNeeded() — app.installation.start on first install
  8. Promise returned from step 7 — same as whenReady()
```

### 5.5 Consent Gate

`src/consent.ts`: `isDataCollectionAllowed(state)` returns `true` only for `PulseDataCollectionConsent.ALLOWED`. `PENDING` and `DENIED` both return `false`.

The consent check runs twice:
1. In `Pulse.init()` before any async work — prevents session/resource construction.
2. In `Pulse.trackEvent()` for the interaction path — runtime re-check.

If consent changes at runtime the host must unmount and remount `PulseProvider` (or call `shutdown()` then re-`init()`). The SDK does not support live consent flipping.

### 5.6 Feature Gate

`src/feature-gate.ts`: `FeatureGate.isEnabled(feature: PulseFeatureName)` maps `PulseFeature.*` names (e.g. `PulseFeature.SESSION`, `PulseFeature.WEB_VITALS`) to entries in the remote `PulseSdkConfig.features` array. A feature is enabled when:

- No matching entry in the features array (default: enabled), OR
- The SDK name `pulse_web_js` is not listed in `sdks`, OR
- `sessionSampleRate === 1`

`sessionSampleRate === 0` disables the feature for 100% of sessions.

`InstrumentationRegistry.shouldInstall(key)`:
- `configEnabled === false` → always false (local kill switch; remote cannot re-enable)
- `configEnabled !== false && gateEnabled` → install

### 5.7 Remote Config Fetch Sequence

```
init()
  └─ SdkConfigFetcher.loadCached()
        └─ localStorage.getItem("pulse_sdk_config")
              ├─ valid JSON + valid shape → mergePulseSdkConfig(parsed) → use
              └─ missing / invalid → DEFAULT_SDK_CONFIG

  └─ [post-init] SdkConfigFetcher.fetchInBackground()
        └─ fetch(configUrl, { "X-API-KEY": apiKey })
              ├─ response.ok + isValidSdkConfig(data) + data.version !== cached.version
              │     → mergePulseSdkConfig(data)
              │     → localStorage.setItem("pulse_sdk_config", JSON.stringify(merged))
              └─ error / no version change → no-op
```

Config URL resolution:
- `localhost` / `10.0.2.2` → `http://localhost:8080/v1/configs/active/`
- Production → `https://pulse-otel-collector.pulse-ux.com/config/projects/{projectId}/pulse-config.json`

### 5.8 OTLP Exporters and Providers

`src/exporters.ts`: `createProviders(exporterConfig, resource, spanProcessors, logProcessors)` builds three providers:

- `WebTracerProvider` → `BatchSpanProcessor` → `OtlpHttpExporter` → `/v1/traces`
- `LoggerProvider` → `BatchLogRecordProcessor` → `OtlpHttpLogExporter` → `/v1/logs`
- `MeterProvider` → `PeriodicExportingMetricReader` → `OtlpHttpMetricExporter` → `/v1/metrics`

Wire format: `protobuf` (default after `useProtobuf` flag) or `json`. `export.format: "json"` is intended for DevTools-readable debugging.

**IndexedDB disk buffering:** When `diskBuffering.enabled !== false` (on by default), the OTLP exporters write to an IndexedDB signal buffer (`IdbSignalBuffer`) before sending over the network. On the next init, `drainBufferedOtlpExports()` replays any unsent batches. Max age and max size are configurable; defaults enforced in `src/constants/disk-buffer.ts`.

**Beacon relay:** If `beaconRelayUrl` is set, `sendBeacon` calls are routed through a relay to avoid the API-key-in-querystring constraint of the native `sendBeacon` API.

**beforeSendData hooks:** `PulseWebBeforeSendConfig` — either a generic `beforeSend(signal) → signal | null` function or a typed object with `beforeSendSpan`, `beforeSendLog`, `beforeSendMetric`. Returning `null` drops the signal. Runs at export time in the exporter pipeline.

### 5.9 Session Lifecycle

`src/session.ts`:

- `SessionProvider` constructs a `sessionId = crypto.randomUUID()` on creation.
- `installationId` is read from `localStorage["pulse_installation_id"]`; generated once on first load.
- `userId` is persisted to `localStorage["pulse_user_id"]`.
- Session rotation: if the tab is backgrounded for > `pageHiddenTimeoutMs` (default 30 min), the next `visibilitychange` to `visible` rotates the session and emits `session.end` / `session.start`.
- `wasNewInstallation()` returns `true` on the very first page load (no prior installation ID).

### 5.10 Public API (`Pulse.*`)

| Method | Description |
|---|---|
| `Pulse.init(config)` | Bootstrap the SDK. Returns `Promise<void>`. |
| `Pulse.shutdown()` | Tear down — uninstall instrumentations, flush, reset singleton. |
| `Pulse.whenReady()` | Resolves when async init finishes, or immediately if already initialized. |
| `Pulse.isInitialized()` | Sync check for whether init has completed. |
| `Pulse.setScreenName(name)` | Update `screen.name` on all subsequent signals. |
| `Pulse.setUserId(id \| null)` | Set / clear user identity. Emits user session transition signals. |
| `Pulse.setUserProperty(key, value)` | Set single user property (`pulse.user.<key>`). |
| `Pulse.setUserProperties(props)` | Batch set user properties. |
| `Pulse.clearUserIdentity()` | Clear persisted user ID and all properties. |
| `Pulse.trackEvent(name, attrs?, timestampMs?)` | Custom event signal (`pulse.type = custom_event`). |
| `Pulse.reportException(error, attrs?)` | Manual non-fatal (`pulse.type = non_fatal`, WARN severity). |
| `Pulse.reportDeviceCrash(error, attrs?)` | Fatal crash signal (`pulse.type = device.crash`, FATAL severity). |
| `Pulse.trackNonFatal(name, attrs?)` | Named non-fatal event (`pulse.type = non_fatal`). |

---

## 6. Test Coverage

### `src/__tests__/sdk-lifecycle.test.ts`

Tests for SDK singleton lifecycle, shutdown guards, restart cycles, and the race condition between `shutdown()` and `finishInit()`:

- `shouldInitializeSuccessfully` — `Pulse.init()` completes and `isInitialized()` returns true
- `shouldBeIdempotentOnDoubleInit` — second `init()` call is a no-op
- `shouldNoOpWhenConsentIsDenied` — `DENIED` state → `isInitialized()` false
- `shouldNoOpWhenConsentIsPending` — `PENDING` state → `isInitialized()` false
- `shouldShutdownAndResetState` — after `shutdown()`, `isInitialized()` returns false
- `shouldAllowReinitAfterShutdown` — shutdown then re-init works cleanly
- `shouldReturnSamePromiseOnConcurrentInit` — concurrent calls during async bootstrap return same promise
- `shouldAbortInitInSSR` — no `window` → init aborts without error
- `shouldHandleShutdownRaceWithFinishInit` — shutdown called before finishInit async chain settles

### `src/__tests__/sdk-public-methods.test.ts`

Unit tests for public SDK methods covering previously-uncovered code paths:

- `trackEvent` — correct `pulse.type = custom_event`, correct attributes emitted
- `reportException` — correct `pulse.type = non_fatal`, `SeverityNumber.WARN`, non-Error coercion
- `reportDeviceCrash` — correct `pulse.type = device.crash`, `SeverityNumber.FATAL`, stack trace
- `trackNonFatal` — correct `pulse.type = non_fatal`, `non_fatal.is_manual = true`
- `setUserProperties` — merge semantics, null removes key
- `clearUserIdentity` — clears persisted userId + properties
- `setScreenName` — no-op before init; updates globalAttrsProcessor after init
- All methods are no-op before `init()` completes

### `src/__tests__/m1.test.ts`

Foundation tests — validates M1 milestone contracts:

- `getOrCreateInstallationId` — creates UUID on first call, returns same on repeat
- `wasNewInstallation` — true first time, false thereafter
- `SessionProvider` — session ID assigned, rotation on backgrounding
- `validateConfig` — throws on missing `apiKey`, passes on valid config
- `isLocalEnvironment` / `resolveEndpointBaseUrl` — local dev key detection
- `buildResource` / `extractProjectId` — resource attribute correctness
- `SdkConfigFetcher.loadCached` — default config when no cache, parsed config from valid cache
- `resolveConfigUrl` — local vs prod URL resolution
- `FeatureGate.isEnabled` — default true when no config, disabled when `sessionSampleRate = 0`
- `PulseGlobalAttributesProcessor` — session ID, screen name, user ID stamped on all signals
- `SessionInstrumentation` — `session.start` emitted on install

### `src/__tests__/m3.test.ts`

Error instrumentation / `device.crash` and `non_fatal` contract tests:

- Uncaught errors captured via `window.onerror` → `device.crash` log emitted
- Unhandled promise rejections → `device.crash` log emitted
- `ErrorInstrumentation.uninstall()` removes event listeners
- Error attributes: `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename`
- SSR guard: instrumentation skips listener install when `window` is undefined

### `src/__tests__/m8.test.ts`

TC 8.x — `pagehide` listener lifecycle:

- Registration count: exactly one `pagehide` listener after init
- BFCache guard: `event.persisted = true` → no flush called
- `forceFlush` called on `pagehide` when `persisted = false`
- `shutdown()` removes the listener
- Restart (shutdown + re-init) rebalances listener count to one
- SSR guard: `window` undefined → no listener registration
- Post-shutdown: pagehide fires after shutdown → no-op (no double flush)

### `src/__tests__/integration-simplified-init.test.ts`

Config surface tests — verifies Web SDK matches Android's minimal public API:

- `apiKey` required — throws without it
- `dataCollectionState` required at init
- `diskBuffering.enabled` defaults on (Android parity)
- `beforeSendData` shape validation (function vs object vs invalid)
- `diskBuffering.maxAgeMs` and `maxCacheSizeBytes` positive-finite validation
- `globalAttributes` and `resourceAttributes` accepted without error

---

## 7. Known Bugs & Gaps

Absorbs `docs/API-CRITIQUE.md` as structured P0/P1/P2 items.

### P0: Before GA / 1.0

**P0:1 — Ambiguous entry point.** `Pulse` is a pre-resolved singleton instance, not a class or factory. `Pulse.init()` is unusual — every peer SDK uses either `init()` (free function) or `new SDK().start()` (class). The current shape forces users to import the runtime even when they only want types. Recommendation: export `init` as a named free function alongside `Pulse` for method calls; make `Pulse.init` an alias. This matches `@sentry/browser` ergonomics exactly.

**P0:2 — Naming drift on capture API.** Four different verbs for "send a signal": `trackEvent`, `trackNonFatal`, `reportException`, `reportDeviceCrash`. Market standard is one verb (Sentry: `capture*`; Datadog: `add*`). Rename to `captureEvent`, `captureException`, `captureCrash`, `captureNonFatal` before 1.0.

**P0:3 — Identity API is split across three setters.** `setUserProperty(key, value)` + `setUserProperties(props)` + `setUserId(id)` + `clearUserIdentity()`. Sentry collapsed this to `setUser({id, ...props})`. Recommend: `setUser({ id, ...props })`, `getUser()`, `clearUser()`. Reduces method count and eliminates the `setUserProperty` / `setUserProperties` redundancy.

**P0:4 — `beforeSendData` naming.** Every peer SDK calls this `beforeSend`. The `Data` suffix adds nothing and breaks Sentry-native muscle memory. Rename to `beforeSend` at config surface.

**P0:5 — Missing `<PulseRouterEvents />` for `/react`.** The Next.js subpath has `<PulseNavigationEvents />`; the React subpath only exports `useRouterTracking` (hook) with no drop-in component equivalent. Forces users to write a null-rendering wrapper component. Add `<PulseRouterEvents />` to `/react` subpath.

**P0:6 — `shutdownOnUnmount` default.** `PulseProvider` defaults `shutdownOnUnmount` to `false` as a documented exception — which means users who accept the default get the wrong behaviour in tests. Default should explicitly be `false` with `true` reserved for test teardown; current code is already `false` but the docs imply it's a caveat rather than an intentional choice.

### P1: First minor after GA

**P1:7 — `globalAttributes` vs `resourceAttributes` scope invisible.** Both exist on `PulseWebConfig` but the difference (per-signal vs per-resource) is invisible from the names. Users will put tenant tags in the wrong one. Either auto-merge into one field or rename: `signalAttributes` (attached per-span/log) vs `resourceAttributes` (OTel Resource, once per init).

**P1:8 — `@dreamhorizon/pulse-web/next` ESM resolution not verified in clean `create-next-app`.** The ecommerce demo uses a webpack alias to resolve the workspace package. This may mask an ESM resolution failure for external consumers. Needs verification before GA.

**P1:9 — No Vite source-map upload.** `withPulseConfig` is Next-only. Vite, CRA, Webpack5, Rollup, and Rspack users must upload source maps manually. Document the manual path; consider `vite-plugin-pulse` in a future minor.

**P1:10 — `reportException` + `reportDeviceCrash` are one-parameter-different.** They differ only in `severityNumber` (WARN vs FATAL) and the `error.filename` attribute on crash. Collapsing to `captureException(err, { level: "fatal" | "warn" })` reduces surface without losing flexibility.

### P2: Nice to have

**P2:11 — Single `<PulseRouter />` for all framework routers.** Auto-detect React Router vs Next.js App Router vs Pages Router and do the right thing. Reduces integration to one component with no subpath import.

**P2:12 — Replace `dataCollectionState` enum with a string union.** `consent: "allowed" | "denied" | "pending"` is half the keystrokes and requires no enum import. The current `PulseDataCollectionConsent` enum is a migration hazard for anyone who tries to tree-shake enum-only imports.

**P2:13 — `PulseAttributes` type drift from OTel `Attributes`.** Currently a Pulse-specific alias. Users copy-pasting OTel snippets hit type mismatches. Align with `@opentelemetry/api` `Attributes` type exactly.

---

## 8. Redundancy & Cleanup Notes

The following planning documents were absorbed into this SPEC.md and deleted (triple-eval: pass 1 — all concepts captured; pass 2 — line-by-line scan; pass 3 — final confirm):

| Deleted path | Content absorbed into |
|---|---|
| `pulse-web-otel/web-sdk-plan/v1/01-foundation/README.md` | §4 (architecture), §6 (test references), this table |
| `pulse-web-otel/web-sdk-plan/v1/01-foundation/sdk-lifecycle.md` | §4, §5.4 (SDK init flow), §5.9 (session lifecycle), §5.10 (public API) |
| `pulse-web-otel/web-sdk-plan/INTEGRATION.md` | §5.3 (config surface), §5.10 (public API table), §7 (P0:5 friction items) |
| `pulse-web-otel/docs/API-CRITIQUE.md` | §7 (Known Bugs & Gaps — full P0/P1/P2 punch list) |

---

## 9. Open Questions

1. **`dataCollectionState` deprecation timeline.** P2:12 proposes `consent: string union`. Before 1.0, should we ship a deprecation warning when the enum form is detected and recommend the new shape?

2. **`globalAttributes` vs `resourceAttributes` merge strategy.** If we auto-merge (P1:7), do signal-level attributes overwrite resource attributes of the same key, or vice versa? Need a decision before touching the `PulseGlobalAttributesProcessor` merge logic.

3. **IndexedDB drain on slow networks.** The drain fires immediately at init. On a slow network, this competes with the `session.start` signal for the first-batch slot. Should drain be delayed until after the first flush, or run in a lower-priority microtask queue?

4. **`Pulse.whenReady()` — should it reject?** Currently it always resolves (even on consent-blocked init). If a consumer awaits `whenReady()` assuming `isInitialized()` will be true afterwards, they will be surprised. Consider: resolve with a boolean, or reject with a typed `PulseInitError` on `DENIED`/`PENDING`.

5. **React 19 / concurrent mode compatibility.** `PulseProvider` calls `Pulse.init()` in a `useEffect`. Under React 19 Strict Mode, effects fire twice in dev. The `_initializing` guard covers the double-init race, but the double `shutdown()` + re-init cycle during Strict Mode teardown has not been explicitly tested.
