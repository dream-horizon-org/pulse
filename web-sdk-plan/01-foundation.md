# Phase 1 — Foundation

**Goal:** A working SDK skeleton that initialises, manages sessions, and exports a real span to the existing Pulse backend over OTLP HTTP. Every subsequent phase builds on this.

**Estimated duration:** Week 1–2
**Prerequisites:** None — this is the starting point.

---

## Scope

**In:**
- Repo scaffold under `pulse-web-otel/`
- `PulseWeb.start(config)` / `PulseWeb.shutdown()`
- Session ID + Installation ID management
- OTEL Resource builder (browser attributes)
- OTLP HTTP exporters (traces, logs, metrics)
- Consent management
- Remote SDK config fetch (`/v1/configs/active`)
- `sendBeacon` flush on page hide

**Out:**
- Any auto-instrumentation (Phase 2)
- Interactions (Phase 2.5)
- Session replay (Phase 3)
- Framework-specific wrappers (Phase 4)

---

## Deliverable

`PulseWeb.start({ endpointBaseUrl, apiKey, serviceName })` sends a heartbeat span. That span is visible in the Pulse ClickHouse dashboard with correct `ProjectId`, `SessionId`, `Platform = 'web'`, and `SDKVersion`.

---

## Implementation Steps

### 1. Repo Scaffold

```
pulse-web-otel/
├── src/
│   ├── index.ts
│   ├── sdk.ts
│   ├── config.ts
│   ├── session.ts
│   ├── resource.ts
│   ├── exporters.ts
│   ├── consent.ts
│   ├── remoteConfig.ts
│   └── utils/
├── package.json
├── tsconfig.json
├── tsup.config.ts
└── vitest.config.ts
```

**`package.json` key fields:**
```json
{
  "name": "@dreamhorizon/pulse-web",
  "version": "0.1.0-alpha.1",
  "type": "module",
  "main": "dist/index.cjs",
  "module": "dist/index.js",
  "types": "dist/index.d.ts"
}
```

**Core dependencies:**
```
@opentelemetry/sdk-trace-web
@opentelemetry/sdk-logs
@opentelemetry/sdk-metrics
@opentelemetry/resources
@opentelemetry/exporter-trace-otlp-http
@opentelemetry/exporter-logs-otlp-http
@opentelemetry/exporter-metrics-otlp-http
@opentelemetry/core
@opentelemetry/api
@opentelemetry/api-logs
```

---

### 2. Config Types (`config.ts`)

```typescript
export interface PulseWebConfig {
  endpointBaseUrl: string;
  apiKey: string;
  serviceName: string;
  serviceVersion?: string;
  dataCollectionState?: PulseDataCollectionConsent;
  globalAttributes?: Record<string, string | number | boolean>;
  configEndpointUrl?: string;
  beforeSend?: (signal: unknown) => unknown | null;
}

export enum PulseDataCollectionConsent {
  ALLOWED = 'ALLOWED',
  DENIED = 'DENIED',
  PENDING = 'PENDING',
}
```

---

### 3. Session Management (`session.ts`)

#### Three Tiers of Identity

| ID | Lifetime | Set by | Purpose |
|---|---|---|---|
| `installation.id` | Browser profile lifetime | SDK auto-generated | Unique browser-device tracking, retention, session linking |
| `session.id` | 30 min inactivity timeout | SDK auto-generated | Group a single usage session |
| `user.id` | Until logout / explicit set | App developer | Link to your own user account |

#### Installation ID

On **Android/iOS**, Installation ID is a UUID stored in SharedPreferences / UserDefaults. It survives app restarts and OS updates, and only resets on full uninstall. It answers: *"which specific install of the app on which device is this?"*

On **web**, there is no install event. The equivalent is a UUID stored in `localStorage` — it identifies a **specific browser profile on a specific device**. Same analytical purpose:
- Counting unique browsers (equivalent to counting unique installs)
- Linking sessions across time ("this browser was here last Tuesday")
- Retention analysis without requiring login
- Grouping all sessions from one browser to investigate a user complaint

**Web-specific durability:** Resets if user clears browser data or uses incognito. Different browsers on the same device get different IDs. This is weaker than mobile but the use case is the same — most users do not clear localStorage regularly.

**Three-tier storage fallback** (mirrors Android's graceful degradation when Keystore is unavailable):

```typescript
// src/foundation/installation-id.ts

const STORAGE_KEY = 'pulse_installation_id';

export function getOrCreateInstallationId(): string {
  // Tier 1: localStorage — persists across browser restarts (best)
  try {
    const existing = localStorage.getItem(STORAGE_KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    localStorage.setItem(STORAGE_KEY, id);
    return id;
  } catch {
    // localStorage blocked: incognito strict mode, storage quota, sandboxed iframe
  }

  // Tier 2: sessionStorage — persists within tab session only
  try {
    const existing = sessionStorage.getItem(STORAGE_KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    sessionStorage.setItem(STORAGE_KEY, id);
    return id;
  } catch {
    // sessionStorage also blocked
  }

  // Tier 3: in-memory — lost on tab close (incognito strict mode)
  if (!_memoryInstallationId) {
    _memoryInstallationId = crypto.randomUUID();
  }
  return _memoryInstallationId;
}

let _memoryInstallationId: string | null = null;
```

| Storage tier | Survives browser restart | Survives tab close | Works in incognito |
|---|---|---|---|
| `localStorage` | ✅ | ✅ | ⚠️ resets each session |
| `sessionStorage` | ❌ | ❌ | ✅ within tab |
| memory | ❌ | ❌ | ✅ within tab |

> **Note:** On web, `installation.id` identifies a browser profile on a device. It is equivalent in purpose to the mobile installation ID but resets on browser data clear or incognito sessions. Treat it as "unique browser-device" not "unique user". Analysts should be aware one user can produce multiple installation IDs (different browsers, cleared storage) — same as mobile where reinstall produces a new ID.

#### Session ID

```
Session ID:
  - Key: 'pulse_session_id' + 'pulse_session_ts' in sessionStorage
  - New session if: no existing ID, OR last activity > 30 min ago
  - Refresh timestamp on each signal emitted
  - Cleared on tab close (sessionStorage behaviour)
  - session.previous_id set to old ID when a new session starts
```

---

### 4. Resource Builder (`resource.ts`)

The OTEL Resource is attached to **every** span, log record, and metric emitted by the SDK. These map directly to ClickHouse materialized columns (`Platform`, `DeviceModel`, `OsVersion`, `SDKVersion`, `ProjectId`, `SessionId`).

#### 4a. Static Resource Attributes (set once at init)

| Attribute Key | Type | Value / Source | Android Equivalent |
|---|---|---|---|
| `service.name` | string | `config.serviceName` | `service.name` |
| `service.version` | string | `config.serviceVersion \|\| 'unknown'` | `service.version` |
| `platform` | string | `"web"` (hardcoded) | `"android"` |
| `rum.sdk.name` | string | `"pulse_web_js"` (hardcoded) | `"pulse_android_java"` |
| `rum.sdk.version` | string | Injected at build time | `rum.sdk.version` |
| `project.id` | string | Extracted from API key | `project.id` |
| `installation.id` | string | `localStorage` → `sessionStorage` → memory (3-tier fallback) | `installation.id` |
| `browser.name` | string | `navigator.userAgentData.brands` / UA parse | replaces `device.model.name` |
| `browser.version` | string | `navigator.userAgentData` / UA parse | replaces `device.model.identifier` |
| `browser.vendor` | string | `navigator.vendor` | replaces `device.manufacturer` |
| `browser.language` | string | `navigator.language` | — (web-only) |
| `os.name` | string | `userAgentData.platform` / UA parse | `os.name` |
| `os.version` | string | UA parse | `os.version` |
| `os.type` | string | `"browser"` (hardcoded) | `"linux"` |
| `device.type` | string | UA detection: `"desktop"` \| `"mobile"` \| `"tablet"` | — (web-only) |
| `device.screen.width` | long | `screen.width` | `device.screen.width` |
| `device.screen.height` | long | `screen.height` | `device.screen.height` |
| `device.screen.aspect_ratio` | string | Computed e.g. `"16:9"` | `device.screen.aspect_ratio` |

#### 4b. Dynamic Global Attributes (appended to every signal by processors)

These are NOT part of the OTEL Resource (which is static). They are added to every span and log record by span processors / log record processors, identical to how Android's `GlobalAttributesSpanAppender`, `SessionIdSpanAppender`, `ScreenAttributesSpanProcessor`, and `NetworkAttributesSpanAppender` work.

| Attribute Key | Type | Value / Source | Android Equivalent |
|---|---|---|---|
| `session.id` | string | `sessionStorage['pulse_session_id']` | `session.id` |
| `session.previous_id` | string | Last session ID before rotation | `session.previous_id` |
| `pulse.metering.session.id` | string | Generated UUID per billing period | `pulse.metering.session.id` |
| `screen.name` | string | Current URL path / active route | `screen.name` |
| `last.screen.name` | string | Previous route before navigation | `last.screen.name` |
| `network.connection.type` | string | `navigator.connection.type` | `network.connection.type` |
| `network.effective_type` | string | `navigator.connection.effectiveType` (`4g`/`3g`/`2g`/`slow-2g`) | replaces `network.connection.subtype` |
| `network.rtt` | long | `navigator.connection.rtt` (ms) | — (web-only) |
| `network.downlink` | double | `navigator.connection.downlink` (Mbps) | — (web-only) |
| `page.url` | string | `window.location.href` (sanitised) | — (web-only) |
| `page.title` | string | `document.title` | — (web-only) |
| `page.referrer` | string | `document.referrer` | — (web-only) |
| `url.path` | string | `window.location.pathname` | — (web-only) |

Any `globalAttributes` passed via `config.globalAttributes` are also merged into every signal, same as Android's `setGlobalAttribute()` API.

#### 4c. Implementation

```typescript
// resource.ts — static, built once at SDK init
export function buildResource(config: PulseWebConfig): Resource {
  const ua = parseUserAgent(); // userAgentData first, fallback to navigator.userAgent

  return new Resource({
    'service.name':            config.serviceName,
    'service.version':         config.serviceVersion ?? 'unknown',
    'platform':                'web',
    'rum.sdk.name':            'pulse_web_js',
    'rum.sdk.version':         SDK_VERSION,
    'project.id':              extractProjectId(config.apiKey),
    'installation.id':         getOrCreateInstallationId(),
    'browser.name':            ua.browserName,
    'browser.version':         ua.browserVersion,
    'browser.vendor':          navigator.vendor,
    'browser.language':        navigator.language,
    'os.name':                 ua.osName,
    'os.version':              ua.osVersion,
    'os.type':                 'browser',
    'device.type':             ua.deviceType,
    'device.screen.width':     screen.width,
    'device.screen.height':    screen.height,
    'device.screen.aspect_ratio': computeAspectRatio(screen.width, screen.height),
  });
}

// global-attributes-processor.ts — dynamic, added to every signal
export class PulseGlobalAttributesProcessor implements SpanProcessor {
  onStart(span: Span): void {
    span.setAttributes({
      'session.id':              getSessionId(),
      'screen.name':             getCurrentScreenName(),
      'last.screen.name':        getLastScreenName(),
      'network.connection.type': getNetworkType(),
      'network.effective_type':  getEffectiveType(),
      'page.url':                sanitizeUrl(window.location.href),
      'page.title':              document.title,
      'url.path':                window.location.pathname,
      ...config.globalAttributes,
    });
  }
}
```

Use `navigator.userAgentData` (Client Hints API) where available; fall back to UA string parsing via a lightweight parser (no heavy library needed).

---

### 5. Exporter Pipeline (`exporters.ts`)

```typescript
// Traces
const spanExporter = new OtlpHttpSpanExporter({
  url: `${config.endpointBaseUrl}/v1/traces`,
  headers: { 'X-API-KEY': config.apiKey },
});
const spanProcessor = new BatchSpanProcessor(spanExporter, {
  scheduledDelayMillis: 5000,
  maxExportBatchSize: 100,
});

// Page unload: flush synchronously via sendBeacon
window.addEventListener('pagehide', () => {
  tracerProvider.forceFlush();
});

// Logs + Metrics follow the same pattern
```

**Important:** The OTLP endpoints (`/v1/traces`, `/v1/logs`, `/v1/metrics`) are the same endpoints the mobile SDKs use. No backend changes required — only CORS headers need to be verified.

---

### 6. SDK Singleton (`sdk.ts`)

```typescript
class PulseWebSDK {
  private static instance: PulseWebSDK;
  private initialized = false;

  static getInstance(): PulseWebSDK { ... }

  start(config: PulseWebConfig): void {
    if (this.initialized) return;  // guard double-init
    // 1. validate config
    // 2. init session
    // 3. build resource
    // 4. init exporters
    // 5. init tracer/logger/meter providers
    // 6. fetch remote config (async, non-blocking)
    // 7. emit init heartbeat span
    this.initialized = true;
  }

  shutdown(): void { ... }
  isInitialized(): boolean { return this.initialized; }
  setDataCollectionState(state: PulseDataCollectionConsent): void { ... }
}

export const PulseWeb = PulseWebSDK.getInstance();
```

---

### 7. Remote Config Fetch (`remoteConfig.ts`)

```typescript
// Non-blocking: fetch in background after init
async function fetchRemoteConfig(baseUrl: string, apiKey: string) {
  const res = await fetch(`${baseUrl}/v1/configs/active`, {
    headers: { 'X-API-KEY': apiKey },
  });
  if (!res.ok) return null;
  return res.json();
}
```

Remote config gates features — store result and check before enabling instrumentations.

---

### 4d. Android Attributes NOT Implemented on Web (and why)

| Android Attribute | Reason Not Available |
|---|---|
| `android.os.api_level` | Android-specific |
| `device.model.identifier` | No device model concept in browser |
| `app.build_name` / `app.build_id` | No native build system on web |
| `android.app.state` | Replaced by `page.visibility_state` via `visibilitychange` event |
| `network.carrier.name/mcc/mnc/icc` | Carrier info not exposed to browsers |
| `storage.free` | `StorageManager` only gives quota, not free space |
| `thread.id` / `thread.name` | JS is single-threaded (Web Workers tracked separately if needed) |

---

## CORS Requirement (Backend)

The Pulse ingest server must respond with:
```
Access-Control-Allow-Origin: *   (or specific customer domain)
Access-Control-Allow-Headers: Content-Type, X-API-KEY
Access-Control-Allow-Methods: POST, OPTIONS
```

Verify this in Phase 1 before proceeding further.

---

## Testing Cycle

### Unit Tests (Vitest)
- `session.ts`: installation ID persists across calls; session ID rotates after 30 min gap; new ID on first call
- `resource.ts`: all expected attributes present; `project.id` correctly extracted from API key
- `config.ts`: invalid config throws; defaults applied correctly
- `sdk.ts`: double `start()` is a no-op; `shutdown()` flushes exporters

### Manual Verification
1. Run a minimal HTML page with `PulseWeb.start()` pointed at a dev ingest endpoint
2. Open Network tab → confirm OTLP POST to `/v1/traces` with 200 response
3. Query ClickHouse: `SELECT * FROM otel.otel_traces WHERE Platform = 'web' LIMIT 5`
4. Confirm: `ProjectId`, `SessionId`, `ServiceName`, `SDKVersion` all populated correctly

---

## Done Criteria

- [ ] `PulseWeb.start({ endpointBaseUrl, apiKey, serviceName })` runs without errors in Chrome, Firefox, Safari
- [ ] A span appears in ClickHouse with `platform = 'web'`
- [ ] Resource attributes present on every span: `service.name`, `rum.sdk.name`, `rum.sdk.version`, `project.id`, `installation.id`, `browser.name`, `browser.version`, `os.name`, `os.version`, `device.type`, `device.screen.width`, `device.screen.height`
- [ ] Dynamic attributes present on every span: `session.id`, `screen.name`, `url.path`, `page.url`, `network.connection.type`
- [ ] `installation.id` persists across page reloads (same value in localStorage)
- [ ] `installation.id` falls back to sessionStorage when localStorage is blocked
- [ ] `installation.id` falls back to in-memory when both storages are blocked (incognito strict mode)
- [ ] `session.id` rotates after 30 min inactivity
- [ ] `session.previous_id` set correctly when a new session starts
- [ ] `screen.name` updates when route changes
- [ ] OTLP endpoint returns 200 (CORS headers correct)
- [ ] Double `start()` call does not create duplicate exporters
- [ ] Unit tests passing

---

## Known Risks

- **CORS**: If the ingest server doesn't allow browser origins, no data will flow. Confirm with backend team before spending time on instrumentations.
- **API key format**: Ensure `extractProjectId()` works with the actual key format issued to web projects.
