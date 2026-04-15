# 01.E — SDK Lifecycle

**What this covers:** The SDK singleton (`PulseWeb`), its initialisation sequence, shutdown flow, and the instrumentation registry that every auto-instrumentation hooks into.

**Files produced:** `src/sdk.ts`, `src/instrumentations/registry.ts`

---

## 6. SDK Singleton (`src/sdk.ts`)

The SDK is a singleton — `PulseWeb.start()` can be called once and subsequent calls are no-ops. This prevents double-instrumentation in hot-reload environments and React StrictMode double-invocations.

```typescript
class PulseWebSDK {
  private static instance: PulseWebSDK;
  private initialized = false;
  private shuttingDown = false;

  static getInstance(): PulseWebSDK {
    if (!PulseWebSDK.instance) {
      PulseWebSDK.instance = new PulseWebSDK();
    }
    return PulseWebSDK.instance;
  }

  start(config: PulseWebConfig): void {
    if (this.initialized || this.shuttingDown) return;  // guard double-init

    // 1. Validate config (throw on missing required fields)
    validateConfig(config);

    // 2. Init session provider (installation ID + session ID)
    this.sessionProvider = new SessionProvider(config);

    // 3. Build OTEL Resource (static browser attributes)
    const resource = buildResource(config);

    // 4. Load cached SDK Config (sync, non-blocking)
    const sdkConfig = this.configFetcher.loadCached();

    // 5. Init feature gate + sampling processor
    const gate = new FeatureGate(sdkConfig);
    const samplingProcessor = new PulseSamplingProcessor(sdkConfig, 'pulse_web_js');
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    // 6. Init OTLP exporters + providers with processors
    this.initProviders(config, resource, [samplingProcessor, filterProcessor]);

    // 7. Register instrumentation registry
    this.registry = new InstrumentationRegistry(this, gate, config.instrumentations);

    // 8. Install all enabled instrumentations
    this.registry.installAll();

    // 9. Fetch fresh SDK Config in background (takes effect next session)
    this.configFetcher.fetchInBackground();

    // 10. Emit init heartbeat span
    this.tracer.startActiveSpan('sdk.init', span => span.end());

    this.initialized = true;
  }

  shutdown(): Promise<void> { ... }
  isInitialized(): boolean { return this.initialized; }
  setDataCollectionState(state: PulseDataCollectionConsent): void { ... }
}

export const PulseWeb = PulseWebSDK.getInstance();
```

---

## 10. Shutdown API

Android calls `uninstall()` on each instrumentation then `sdk.shutdown()`. iOS additionally clears persistent storage and locks re-initialisation. The web SDK follows the same pattern.

```typescript
async shutdown(options?: { clearPersisted?: boolean }): Promise<void> {
  if (!this.initialized) return;
  this._isShuttingDown = true;

  // Step 1: Uninstall all instrumentations (reverse install order)
  this.registry.uninstallAll();

  // Step 2: Force flush all providers — wait for in-flight batches
  await Promise.all([
    this.tracerProvider.forceFlush(),
    this.loggerProvider.forceFlush(),
    this.meterProvider.forceFlush(),
  ]);

  // Step 3: Optionally clear IndexedDB signal buffer
  if (options?.clearPersisted) {
    await clearSignalBuffer();
  }

  // Step 4: Lock out re-initialisation
  this._isShutdown = true;
  this.initialized = false;
}
```

**When to call:**
- SPAs that tear down on hot reloads (test environments)
- Explicit user logout — clear session + installation data
- Framework integrations can call `PulseWeb.shutdown()` in their unmount lifecycle

---

## 11. Instrumentation Registry

Every instrumentation — auto or opt-in — implements a common interface. This keeps the SDK core decoupled from any specific instrumentation logic.

```typescript
// src/instrumentations/registry.ts

export interface PulseInstrumentation {
  readonly name: string;
  install(sdk: PulseWebSDK): void;
  uninstall(): void;
}

export class InstrumentationRegistry {
  private installed: PulseInstrumentation[] = [];

  installAll(): void {
    const config = this.initConfig.instrumentations ?? {};

    // Default all to enabled; respect explicit false overrides
    const shouldInstall = (key: string) =>
      this.gate.isEnabled(key as PulseFeatureName) &&
      (config[key]?.enabled ?? true);

    if (shouldInstall('errors'))        this.install(new ErrorsInstrumentation());
    if (shouldInstall('network'))       this.install(new NetworkInstrumentation());
    if (shouldInstall('clicks'))        this.install(new ClicksInstrumentation());
    if (shouldInstall('webVitals'))     this.install(new WebVitalsInstrumentation());
    if (shouldInstall('navigation'))    this.install(new NavigationInstrumentation());
    if (shouldInstall('session'))       this.install(new SessionInstrumentation());
    if (shouldInstall('interactions'))  this.install(new InteractionsInstrumentation());
    // sessionReplay is opt-in — requires explicit enabled: true
    if (config.sessionReplay?.enabled) this.install(new SessionReplayInstrumentation());
  }

  private install(instrumentation: PulseInstrumentation): void {
    instrumentation.install(this.sdk);
    this.installed.push(instrumentation);
  }

  uninstallAll(): void {
    // Reverse order to mirror install
    [...this.installed].reverse().forEach(i => i.uninstall());
    this.installed = [];
  }
}
```

Remote SDK Config (Module 4) can override `enabled` server-side without an SDK release — the `FeatureGate` reads from the remote config and `installAll()` is called with the updated gate on next page load.

---

## CORS Requirement (Backend)

The Pulse ingest server must respond with these headers — without them, **no data flows from any browser**. Verify this before spending time on any instrumentation.

```
Access-Control-Allow-Origin:  *   (or restrict to specific customer domains)
Access-Control-Allow-Headers: Content-Type, X-API-KEY
Access-Control-Allow-Methods: POST, OPTIONS
```

Confirm via:
```bash
curl -I -X OPTIONS https://ingest.pulse.io/v1/traces \
  -H "Origin: https://example.com" \
  -H "Access-Control-Request-Method: POST"
# → should include Access-Control-Allow-Origin in response
```

---

## Testing Cycle

### Unit Tests (Vitest)

- `sdk.ts`: double `start()` is a no-op; `shutdown()` flushes exporters; post-shutdown `start()` rejected
- `registry.ts`: `instrumentations.errors.enabled: false` prevents error instrumentation from installing
- `session.ts`: covered in [identity.md](./identity.md)
- `resource.ts`: covered in [resource.md](./resource.md)
- `config.ts`: invalid config throws; defaults applied correctly

### Manual Verification

1. Run a minimal HTML page with `PulseWeb.start()` pointed at a dev ingest endpoint
2. Open Network tab → confirm OTLP POST to `/v1/traces` with 200 response
3. Query ClickHouse: `SELECT * FROM otel.otel_traces WHERE Platform = 'web' LIMIT 5`
4. Confirm: `ProjectId`, `SessionId`, `ServiceName`, `SDKVersion` all populated correctly

---

## Done Criteria

**Core**
- [ ] `PulseWeb.start({ endpointBaseUrl, apiKey, serviceName })` runs without errors in Chrome, Firefox, Safari
- [ ] A span appears in ClickHouse with `platform = 'web'`
- [ ] Double `start()` call does not create duplicate exporters or instrumentations
- [ ] OTLP endpoint returns 200 (CORS headers correct)

**Shutdown**
- [ ] `await PulseWeb.shutdown()` force-flushes all providers
- [ ] All instrumentation `uninstall()` methods called (in reverse order)
- [ ] Post-shutdown `start()` call is rejected (does not reinitialise)

**Instrumentation Registry**
- [ ] `instrumentations.errors.enabled: false` prevents error instrumentation from installing
- [ ] `instrumentations.sessionReplay.enabled: false` (default) produces no replay data
- [ ] Remote SDK Config `isEnabled()` gates respected by registry

**Unit tests passing for all of the above**

---

## Known Risks

- **CORS**: If the ingest server doesn't allow browser origins, no data will flow. Confirm with backend team before spending time on instrumentations. This is the single most common failure mode.
- **API key format**: Ensure `extractProjectId()` works with the actual key format issued to web projects — parse and verify in unit tests before going further.
- **Double init in React StrictMode**: React 18 StrictMode double-invokes effects. The singleton guard (`if (this.initialized) return`) handles this, but verify with a React test app.
