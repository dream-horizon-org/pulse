# M1 — Foundation: SDK Core Pipeline

## Context
Implements the real SDK core: identity, session management, OTLP export pipeline, batching, gzip compression, IndexedDB persistence, the SDK singleton lifecycle, and the session instrumentation. After M1 the demo app sends a real `session.start` log to ClickHouse with `platform='web'`. No auto-instrumentations yet — just the pipeline and session signals.

## Prerequisites
- P0 complete: `pulse-web-otel/` exists, `yarn install` passes, stubs in place
- A running Pulse ingest endpoint (local or dev) — needed for CORS verification

## Spec Docs to Read First (read all before writing any code)
1. `pulse-web-otel/web-sdk-plan/v1/01-foundation/scaffold.md` — `PulseWebConfig` interface (required fields, validation rules)
2. `pulse-web-otel/web-sdk-plan/v1/01-foundation/identity.md` — Installation ID 3-tier storage + Session Provider
3. `pulse-web-otel/web-sdk-plan/v1/01-foundation/resource.md` — OTEL Resource attrs + `GlobalAttrsProcessor`
4. `pulse-web-otel/web-sdk-plan/v1/01-foundation/pipeline.md` — OTLP exporters, batch config, gzip, sendBeacon, IndexedDB
5. `pulse-web-otel/web-sdk-plan/v1/01-foundation/sdk-lifecycle.md` — SDK singleton 10-step init + shutdown + registry
6. `pulse-web-otel/web-sdk-plan/v1/01-foundation/session.md` — `session.start` / `session.end` signal contract
7. `pulse-web-otel/web-sdk-plan/v1/01-foundation/sdk-config.md` — SdkConfigFetcher, SamplingProcessor, FeatureGate, SignalFilterProcessor

## Files to Create / Replace

| File | Spec doc | Replaces stub? |
|---|---|---|
| `src/config.ts` | `scaffold.md` | Yes — add `validateConfig()` that throws on missing required fields |
| `src/session.ts` | `identity.md` | Yes — full implementation |
| `src/resource.ts` | `resource.md` | Yes — full implementation |
| `src/exporters.ts` | `pipeline.md` | Yes — full implementation |
| `src/persistence/indexed-db.ts` | `pipeline.md` | New file |
| `src/processors/global-attrs-processor.ts` | `resource.md` | New file |
| `src/processors/sampling-processor.ts` | `sdk-config.md` | New file |
| `src/processors/signal-filter-processor.ts` | `sdk-config.md` | New file |
| `src/consent.ts` | `scaffold.md` | Yes — consent guard check |
| `src/remote-config.ts` | `sdk-config.md` | Yes — full implementation |
| `src/feature-gate.ts` | `sdk-config.md` | Yes — full implementation |
| `src/instrumentation-registry.ts` | `sdk-lifecycle.md` | Yes — full implementation |
| `src/instrumentations/session.ts` | `session.md` | New file |
| `src/sdk.ts` | `sdk-lifecycle.md` | Yes — 10-step init, replaces stub |
| `src/__tests__/m1.test.ts` | All above | New file |

## Key Implementation Notes

### Identity (`src/session.ts`)
- `getOrCreateInstallationId()`: try `localStorage.getItem('pulse_iid')`, then `sessionStorage`, then in-memory; write to all tiers on create
- `SessionProvider.getSessionId()`: 30-min inactivity timer reset on every call; rotate = new UUID + set `previous_id`
- BFCache: `addEventListener('pageshow', e => { if (e.persisted) resumeSession() })` — do NOT emit `session.start` on BFCache restore
- `pagehide` with `e.persisted === false` only → emit `session.end`

### OTLP Pipeline (`src/exporters.ts`)
- Headers on all three exporters: `{ 'Content-Type': 'application/json', 'x-api-key': config.apiKey }`
- BatchSpanProcessor defaults: `scheduledDelayMillis: 5000, maxQueueSize: 2048, maxExportBatchSize: 512`
- `sendBeacon` flush: `addEventListener('pagehide', () => provider.forceFlush())`
- gzip: `new CompressionStream('gzip')` — feature-detect with `try/catch`; fall back to uncompressed if unavailable

### IndexedDB (`src/persistence/indexed-db.ts`)
- Store name: `pulse_signal_buffer`, key: `id` (autoIncrement)
- Schema: `{ id, signalType, payload: string, timestamp: number, retryCount: number }`
- `PersistenceExporterDecorator`: on export failure → write to IDB; on `SDK.start()` → `drainBuffer()` reads IDB and re-exports
- Prune entries older than `diskBuffering.maxAgeMs` (default 24h) on drain
- Only active when `config.diskBuffering.enabled === true` (default: false)

### SDK Singleton (`src/sdk.ts`) — 10-step init
```
1. validateConfig(config)           → throw if missing endpointBaseUrl/apiKey/serviceName
2. new SessionProvider(config)      → identity + session
3. buildResource(config)            → OTEL Resource with 18 static attrs
4. configFetcher.loadCached()       → sync read from localStorage
5. new FeatureGate(sdkConfig)       → per-instrumentation gates
6. new PulseSamplingProcessor(...)  → session-level sample decision
7. new SignalFilterProcessor(...)   → attribute drop/add
8. initProviders(...)               → TracerProvider + LoggerProvider + MeterProvider
9. new InstrumentationRegistry(...) → .installAll()
10. configFetcher.fetchInBackground() + emit heartbeat span
```
- Guard: `if (this.initialized || this.shuttingDown) return;`
- `shutdown()`: uninstallAll → forceFlush all providers → optionally clear IDB → set `initialized = false`

### Session Instrumentation (`src/instrumentations/session.ts`)
- `install()`: emit `pulse.type: 'session.start'` log with `session.id`, `installation.id`, `platform: 'web'`
- Listen to session rotation events → emit `session.end` (with `session.duration_ms`, `screens_visited`) + new `session.start`
- `pagehide` (non-BFCache) → emit `session.end`

## Done Criteria
- [ ] `PulseWeb.start()` runs without errors in Chrome, Firefox, Safari
- [ ] `session.start` log in ClickHouse: `platform = 'web'`, correct `project.id`, `session.id`, `rum.sdk.version`
- [ ] `installation.id` survives page reload (localStorage)
- [ ] CORS verified: `OPTIONS /v1/traces` returns `Access-Control-Allow-Origin`
- [ ] Double `start()` is a no-op (no duplicate exporters or instrumentations)
- [ ] `pagehide` triggers `session.end` log
- [ ] BFCache restore does NOT emit duplicate `session.start`
- [ ] `diskBuffering.enabled: true` → failed export writes to IndexedDB; drains on next `start()`
- [ ] `await PulseWeb.shutdown()` force-flushes all providers; post-shutdown `start()` rejected
- [ ] `instrumentations.errors.enabled: false` prevents that instrumentation from installing
- [ ] Unit tests green: identity 3-tier fallback, session rotation, resource attrs, config validation, singleton guard

## Verification

### Unit tests
```bash
cd pulse-web-otel && yarn test --run src/__tests__/m1.test.ts
```

### E2E tests (no real ingest needed — OTLP intercepted by Playwright)
```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn playwright install --with-deps chromium   # first time only
yarn e2e --grep "@M1" --project=chromium
# Or from SDK root: yarn workspace ecommerce-demo e2e --grep "@M1"
# Add --headed to open a visible browser for debugging
```

### Manual + ClickHouse (against real ingest)
```bash
yarn build && yarn workspace ecommerce-demo dev
# Set VITE_PULSE_ENDPOINT_BASE_URL to real ingest, open http://localhost:3002
```
```sql
SELECT platform, project_id, session_id, rum_sdk_version
FROM otel.otel_logs
WHERE pulse_type = 'session.start' AND platform = 'web'
LIMIT 5;
```
Update `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M1 checkboxes when all pass.
