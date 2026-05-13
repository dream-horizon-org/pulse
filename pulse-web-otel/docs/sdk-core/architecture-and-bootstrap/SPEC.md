# SDK Core — Architecture and bootstrap — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/architecture-and-bootstrap/SPEC.md`

---

## 1. Goal

Describe **bootstrap architecture** and the **ordered `Pulse.init` sequence** from host call through provider wiring, registry install, and background config fetch.

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md).

---

## 3. Requirements

Covers **R1** (init completion), **R5** (shutdown teardown of listeners + flush), and sequencing implied by **R2–R4, R8–R10** — see [`../requirements/SPEC.md`](../requirements/SPEC.md).

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

### 5.1 SDK init flow (sequence)

Related: [`../config-and-consent/SPEC.md`](../config-and-consent/SPEC.md) · [`../exporters-and-persistence/SPEC.md`](../exporters-and-persistence/SPEC.md) · [`../remote-config-features-and-sampling/SPEC.md`](../remote-config-features-and-sampling/SPEC.md)

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

---

## 6. Test Coverage

[`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) — `sdk-lifecycle.test.ts`, `m8.test.ts` (pagehide), `integration-simplified-init.test.ts`.

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md).

---

## 8. Redundancy & Cleanup Notes

Prior `web-sdk-plan/v1/01-foundation/README.md` content rolled into this document and [`../test-coverage/SPEC.md`](../test-coverage/SPEC.md).

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
