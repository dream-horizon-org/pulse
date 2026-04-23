
# Pulse Web SDK — Build & Publish Plan

## Context: What We're Working With

Before the plan, key facts from codebase analysis:

**The React Native SDK is NOT reusable for web** — it's a thin TypeScript bridge over native Kotlin/Swift code via TurboModule. The web SDK must be **pure TypeScript built on OTEL JS packages** that run in browsers.

**What the backend already gives us for free:**
- OTLP HTTP endpoint (port 4318) — the web SDK just points at it, zero backend changes
- ClickHouse schema already has `Platform`, `SDKVersion`, `SessionId`, `ProjectId` columns — web data lands in the same tables
- Semantic conventions (`pulse.type`, `project.id`, etc.) already defined in `pulse-semconv`
- Remote config endpoint (`/v1/configs/active`) — web SDK can consume the same config

---

## Architecture Overview

```
Browser
  └─ pulse-web SDK (TypeScript)
       ├─ Auto-Instrumentations (OTEL official + custom)
       │    ├─ Document Load (page perf)
       │    ├─ Fetch / XHR (network)
       │    ├─ User Interactions (clicks)
       │    ├─ Long Tasks
       │    ├─ JS Errors / Promise rejections
       │    ├─ Web Vitals (LCP, CLS, FID, INP)
       │    ├─ Session Replay (rrweb)
       │    └─ SPA Route changes
       ├─ Exporters
       │    ├─ OtlpHttpSpanExporter      → /v1/traces
       │    ├─ OtlpHttpLogRecordExporter → /v1/logs
       │    └─ OtlpHttpMetricExporter    → /v1/metrics
       └─ Public API
            └─ PulseWeb.start() / trackEvent() / setUserId() / ...
```

---

## Core OTEL Packages

These are the official OpenTelemetry JS packages — the same family that Android/iOS SDKs use on the backend, just the browser-targeted variants:

| Package | Purpose |

|---|---|
| `@opentelemetry/sdk-trace-web` | Web tracer provider, context propagation |
| `@opentelemetry/sdk-logs` | Log record SDK |
| `@opentelemetry/sdk-metrics` | Metrics SDK |
| `@opentelemetry/resources` | Resource attributes (device, OS, SDK version) |
| `@opentelemetry/exporter-trace-otlp-http` | Ships spans to OTLP HTTP |
| `@opentelemetry/exporter-logs-otlp-http` | Ships logs to OTLP HTTP |
| `@opentelemetry/exporter-metrics-otlp-http` | Ships metrics to OTLP HTTP |
| `@opentelemetry/instrumentation-document-load` | Page load + resource timing spans |
| `@opentelemetry/instrumentation-fetch` | Fetch API auto-instrumentation |
| `@opentelemetry/instrumentation-xml-http-request` | XHR auto-instrumentation |
| `@opentelemetry/instrumentation-user-interaction` | DOM click/interaction spans |
| `@opentelemetry/instrumentation-long-task` | Long tasks (>50ms) via PerformanceObserver |
| `web-vitals` | LCP, CLS, FID, INP, TTFB, FCP |
| `rrweb` | DOM recording for session replay |

---

## Phase 1 — Foundation (Week 1–2)

**Goal:** Bare SDK that can initialize, attach to a project, and export a heartbeat span.

### 1.1 Repo Structure

Create `pulse-web-otel/` in the monorepo, mirroring the RN SDK layout:

```
pulse-web-otel/
├── src/
│   ├── index.ts              # Public API entry point
│   ├── sdk.ts                # Core init logic (PulseWeb singleton)
│   ├── config.ts             # Config types + validation
│   ├── session.ts            # Session & installation ID management
│   ├── resource.ts           # OTEL Resource builder (device, browser, OS)
│   ├── exporters.ts          # OTLP exporters wired together
│   ├── consent.ts            # Data collection consent (matches mobile API)
│   ├── semconv.ts            # Semantic convention constants
│   ├── instrumentations/     # All auto-instrumentations
│   ├── integrations/         # Framework wrappers (React, Vue, Next.js)
│   └── utils/                # URL sanitizer, attribute helpers, etc.
├── examples/
│   ├── vanilla/              # Plain HTML/JS example
│   ├── react-app/            # CRA / Vite React
│   └── nextjs-app/           # Next.js 14 App Router
├── package.json
├── tsconfig.json
├── rollup.config.ts          # Build config
└── vitest.config.ts          # Test config
```

### 1.2 Config API

Mirrors the mobile SDK shape exactly so the mental model is consistent:

```typescript
PulseWeb.start({
  endpointBaseUrl: 'https://ingest.pulse.io',
  apiKey: 'pk_...',
  serviceName: 'my-web-app',
  serviceVersion: '1.2.3',

  // Consent (same enum as mobile)
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,

  // Per-instrumentation opt-out
  instrumentations: {
    documentLoad: true,
    network: true,        // fetch + XHR
    interactions: true,   // clicks
    errors: true,
    webVitals: true,
    sessionReplay: false, // opt-in (heavier)
    longTasks: true,
  },

  // SPA routing
  routing: {
    mode: 'history',              // 'history' | 'hash' | 'none'
    framework: 'react-router-v6', // or 'next' | 'vue-router' | 'manual'
  },

  // Privacy
  privacy: {
    maskAllInputs: true,
    maskTextSelector: '.sensitive',
    allowedUrls: ['https://api.myapp.com'],   // only trace these
    blockedUrls: ['https://analytics.google.com'],
    sanitizeUrl: (url) => url,
  },

  // Global attributes merged into all signals
  globalAttributes: { environment: 'production' },

  // Before-send hook (same as mobile)
  beforeSendData: (signal) => signal,  // return null to drop

  // Remote config
  configEndpointUrl: 'https://ingest.pulse.io',
})
```

### 1.3 Resource Attributes

The OTEL Resource populates the ClickHouse `DeviceModel`, `OsVersion`, `Platform`, `SDKVersion` columns:

```typescript
{
  'service.name': config.serviceName,
  'service.version': config.serviceVersion,
  'platform': 'web',
  'rum.sdk.name': 'pulse_web_js',
  'rum.sdk.version': PULSE_SDK_VERSION,
  'browser.name': 'Chrome',          // from User-Agent
  'browser.version': '124.0',
  'os.name': 'macOS',
  'os.version': '14.4',
  'device.type': 'desktop',          // desktop | mobile | tablet
  'screen.width': 1920,
  'screen.height': 1080,
  'url.full': window.location.href,  // starting URL
  'project.id': extractFromApiKey(config.apiKey),
  'installation.id': getOrCreateInstallationId(),  // localStorage
}
```

### 1.4 Session Management

Web sessions need special handling (no app lifecycle events):

```
Session ID lifecycle:
- New session on first page load (or after 30min gap)
- Stored in sessionStorage (cleared on tab close)
- Linked by session.id attribute on every signal

Installation ID lifecycle:
- Persistent across sessions
- Stored in localStorage
- Cleared only on explicit opt-out/logout
```

### 1.5 Exporter Pipeline

Critical: use `sendBeacon()` for page unload to avoid data loss. This is the web equivalent of disk buffering on mobile:

```typescript
BatchSpanProcessor {
  exporter: OtlpHttpSpanExporter('/v1/traces'),
  scheduledDelayMillis: 5000,
  maxExportBatchSize: 100,
  onPageHide: () => flushWithBeacon()  // navigator.sendBeacon()
}
```

---

## Phase 2 — Auto-Instrumentations (Week 3–4)

### 2.1 Error Tracking (`pulse.type: device.crash` / `non_fatal`)

Equivalent to Android's `CrashInstrumentation` and iOS's `KSCrash`:

```typescript
// Unhandled JS errors → pulse.type: device.crash
window.addEventListener('error', (event) => {
  // Extract stack, file, line, column
  // Create log record with pulse.type = 'device.crash'
  // Include: error.message, error.stack, error.type
  // Include: url, component (if React Error Boundary)
})

// Unhandled Promise rejections → pulse.type: non_fatal
window.addEventListener('unhandledrejection', (event) => {
  // pulse.type = 'non_fatal'
})

// Console.error capture → pulse.type: non_fatal
// Patch console.error to also emit log records
```

### 2.2 Network Instrumentation

Existing OTEL packages handle most of this. Custom additions:
- GraphQL operation name extraction (same as RN SDK's `graphql-helper.ts`)
- Request/response header capture (opt-in, configurable allowlist)
- Blocked URL filtering (don't trace Pulse's own OTLP endpoints)
- Payload size capture

### 2.3 User Interaction Tracking (`pulse.type: app.click`)

The OTEL `@opentelemetry/instrumentation-user-interaction` package captures clicks. Extended with:

```typescript
// Rage click detection (same algorithm as mobile)
// Logic: 3+ clicks on same element within 700ms = rage click
class RageClickDetector {
  // Emits pulse.type: app.click with click.is_rage: true
  // Increments click.rage_count
  // Normalized coordinates: app.screen.coordinate.nx, .ny
}

// Dead click detection
// A click that causes no DOM mutation, navigation, or network request
// = dead click → click.type: 'dead'
```

### 2.4 Web Vitals (`pulse.type: web_vital`)

New signal type specific to web (no mobile equivalent):

```typescript
// via web-vitals package
// Each metric → OTLP Metric (Gauge)
{
  'metric.name': 'LCP' | 'CLS' | 'FID' | 'INP' | 'TTFB' | 'FCP',
  'metric.value': number,
  'metric.rating': 'good' | 'needs-improvement' | 'poor',
  'url.path': '/checkout',
}
```

These map to `otel.otel_metrics_gauge` in ClickHouse.

### 2.5 Page Load / Navigation

```typescript
// Initial page load timing via Navigation Timing API
// Creates spans matching mobile's screen_load / screen_interactive

pulse.type: screen_load        → span from navigation start → load event
pulse.type: screen_interactive → span until TTI (Time to Interactive)

// SPA route changes (pushState/popstate)
pulse.type: screen_session     → time spent on current "screen" (SPA route)
```

### 2.6 Long Tasks (`pulse.type: app.jank.slow`)

Same concept as Android's `SlowRenderingInstrumentation`:

```typescript
// PerformanceObserver for 'longtask' entries (> 50ms)
// Maps to pulse.type: app.jank.slow (matching mobile semantic conventions)
{
  'app.jank.type': 'long_task',
  'duration': 127,  // ms
  'url.path': '/feed',
}
```

---

## Phase 2.5 — Interactions (Week 4–5)

**Goal:** Port the full server-driven interaction tracking system to web. This is the most direct feature parity item with mobile — the matching algorithm is pure logic with no native dependencies, so the web implementation is a clean TypeScript port.

### What Interactions Are

Interactions track **multi-step user journeys** defined on the server. Example: `checkout_started` → `payment_initiated` → `payment_success`. The SDK fetches the sequence definition from a CDN config, then matches incoming `trackEvent()` calls against it in real-time. When a full sequence is matched, an OTel span is emitted with APDEX score, duration category (Excellent/Good/Average/Poor), and error status.

**Why this matters for web:** Funnels and user flows are arguably more important on web than mobile (checkout flows, onboarding, search → product → cart → purchase). This should be in v1.

---

### 2.5.1 Architecture

```
InteractionInstrumentation (web)
├── InteractionConfigFetcher     → fetch() from CloudFront CDN
├── InteractionManager           → one InteractionEventsTracker per config
│   └── InteractionEventsTracker → state machine, runs matchSequence()
│       └── InteractionUtil      → pure matching algorithm (TypeScript port)
└── On sequence complete:
    → Create OTel span (pulse.type: "interaction")
    → Set APDEX, user_category, is_error, complete_time
    → Export via existing OTLP exporter
```

The same CDN URL that iOS/Android fetch from works unchanged for web:
```
GET https://cdn.pulse.io/config/projects/{projectId}/interaction-config.json
```

---

### 2.5.2 Config Fetching

```typescript
// Same endpoint as mobile — no backend changes needed
async function fetchInteractionConfigs(
  configUrl: string,
  headers: Record<string, string>
): Promise<InteractionConfig[]> {
  const url = `${configUrl}/config/projects/${projectId}/interaction-config.json`;
  const res = await fetch(url, { headers });
  if (!res.ok) return [];
  return res.json();
}
```

Fetched once at SDK init, same as mobile. Page reload picks up any config changes.

---

### 2.5.3 InteractionConfig Data Model (TypeScript)

Direct port of the Java/Swift model — shape is identical:

```typescript
interface InteractionConfig {
  id: number;
  name: string;
  events: InteractionEvent[];
  globalBlacklistedEvents: InteractionEvent[];
  uptimeLowerLimitInMs: number;  // Excellent threshold
  uptimeMidLimitInMs: number;    // Good threshold
  uptimeUpperLimitInMs: number;  // Average threshold (Poor if above)
  thresholdInMs: number;         // Max gap between consecutive events before timeout
}

interface InteractionEvent {
  name: string;
  props: InteractionEventProp[] | null;
  isBlacklisted: boolean;
}

interface InteractionEventProp {
  name: string;
  value: string;
  operator: 'EQUALS' | 'NOTEQUALS' | 'CONTAINS' | 'NOTCONTAINS' | 'STARTSWITH' | 'ENDSWITH';
}
```

---

### 2.5.4 Sequence Matching Algorithm (TypeScript Port)

`matchSequence()` is pure logic — identical to iOS/Android, just TypeScript:

```typescript
function matchEvent(
  incoming: { name: string; props: Record<string, string> },
  configEvent: InteractionEvent
): boolean {
  if (incoming.name !== configEvent.name) return false;
  if (!configEvent.props) return true;
  return configEvent.props.every(filter => {
    const val = incoming.props[filter.name] ?? '';
    switch (filter.operator) {
      case 'EQUALS':       return val === filter.value;
      case 'NOTEQUALS':    return val !== filter.value;
      case 'CONTAINS':     return val.includes(filter.value);
      case 'NOTCONTAINS':  return !val.includes(filter.value);
      case 'STARTSWITH':   return val.startsWith(filter.value);
      case 'ENDSWITH':     return val.endsWith(filter.value);
    }
  });
}
```

**State machine per tracker (`InteractionEventsTracker.ts`):**

```
State: IDLE
  ↓  first event matches config.events[0]
State: ONGOING (sequence position = 1) → start thresholdInMs timer
  ↓  next event matches config.events[1] → reset timer
  ...
State: ONGOING (sequence position = N-1)
  ↓  final event matches
State: COMPLETED → create OTel span, reset to IDLE

Error paths:
  - globalBlacklistedEvent arrives during ONGOING → emit error, reset to IDLE
  - thresholdInMs timer fires                    → emit timeout error, reset to IDLE
  - wrong event breaks sequence order            → emit broken-sequence error, reset to IDLE
```

---

### 2.5.5 OTel Span Output

Identical attributes to mobile — same ClickHouse columns, same dashboard queries, **no UI changes needed**:

```typescript
// pulse.type: "interaction"
span.setAttributes({
  'pulse.type': 'interaction',
  'pulse.interaction.id': crypto.randomUUID(),
  'pulse.interaction.name': config.name,
  'pulse.interaction.config.id': config.id,
  'pulse.interaction.apdex_score': calculateApdex(durationMs, config),
  'pulse.interaction.user_category': getUserCategory(durationMs, config),
  'pulse.interaction.complete_time': durationNs,
  'pulse.interaction.is_error': isError,
  // Each matched event's properties become span attributes too
})

// Each event in the sequence → span event with name, props, timestamp
matchedEvents.forEach(e => span.addEvent(e.name, e.props, e.timestamp));
```

APDEX + category mapping (same thresholds as mobile):
```typescript
function calculateApdex(durationMs: number, config: InteractionConfig): number {
  if (durationMs <= config.uptimeLowerLimitInMs) return 1.0; // Satisfied
  if (durationMs <= config.uptimeUpperLimitInMs) return 0.5; // Tolerated
  return 0.0;                                                // Frustrated
}

function getUserCategory(durationMs: number, config: InteractionConfig): string {
  if (durationMs <= config.uptimeLowerLimitInMs) return 'Excellent';
  if (durationMs <= config.uptimeMidLimitInMs)   return 'Good';
  if (durationMs <= config.uptimeUpperLimitInMs) return 'Average';
  return 'Poor';
}
```

---

### 2.5.6 Integration with `trackEvent()`

No new public API needed. Interactions piggyback on `PulseWeb.trackEvent()`:

```typescript
// App code (same API as mobile)
PulseWeb.trackEvent('checkout_started', { cartValue: '120' });
PulseWeb.trackEvent('payment_initiated', { method: 'card' });
PulseWeb.trackEvent('payment_success', { orderId: 'ORD-123' });

// Internally, each trackEvent() is routed to:
// 1. Log record export (custom event, as always)
// 2. InteractionManager.addEvent() — checked against all active configs
```

---

### 2.5.7 Web-Specific Differences from Mobile

| Aspect | Mobile | Web |
|---|---|---|
| Config fetch | Retrofit / URLSession | `fetch()` |
| Async model | Coroutines / Combine | `async/await` + `Promise` |
| Timer | `Handler` / `DispatchQueue` | `setTimeout` / `clearTimeout` |
| Timestamp | Nanosecond clock | `performance.now()` × 1,000,000 |
| Config refresh | App relaunch | Page reload |
| Thread safety | Mutex / Actor | Single-threaded (no issue) |

---

## Phase 3 — Session Replay (Week 6–7)

This is the most complex feature. On mobile it's screenshot-based; on web we use **DOM recording via rrweb** (same approach used by PostHog, LogRocket, Highlight.io).

### 3.1 Architecture

```
rrweb records DOM mutations (not screenshots)
  → delta snapshots (highly compressed)
  → batch every 5s
  → send as pulse.type: session_replay log records
  → stored in ClickHouse otel_logs
  → pulse-ui player reconstructs the DOM

Benefits over screenshot approach:
- ~100x smaller payload than screenshots
- Can replay text/form interactions (with masking)
- No OCR needed
```

### 3.2 Privacy by Default

```typescript
rrweb.record({
  maskAllInputs: true,           // NO keystrokes captured by default
  blockClass: 'pulse-block',     // <div class="pulse-block"> → black box
  ignoreClass: 'pulse-ignore',   // elements not recorded at all
  maskTextClass: 'pulse-mask',   // text replaced with *****
  // Passwords auto-detected by input[type=password]
})
```

### 3.3 Compression & Transport

rrweb events → `pako.gzip()` → base64 → OTLP log body attribute

Session replay data can be large; mitigate with:
- Chunk-based upload (don't buffer entire session in memory)
- Incremental snapshots only (rrweb handles this)
- Sampling (e.g. only capture 10% of sessions, configurable)
- `sendBeacon` for end-of-session flush

---

## Phase 4 — Framework Integrations (Week 7)

### 4.1 React Integration

```typescript
import { PulseProvider, PulseErrorBoundary } from '@dreamhorizon/pulse-web/react';

// App wrapper
<PulseProvider config={pulseConfig}>
  <App />
</PulseProvider>

// Error boundary (matches React Native SDK's ErrorBoundary)
<PulseErrorBoundary fallback={<ErrorPage />}>
  <FeatureComponent />
</PulseErrorBoundary>

// React Router v6 integration
// Automatically tracks route changes as screen_session/screen_load
import { usePulseNavigationTracking } from '@dreamhorizon/pulse-web/react';
usePulseNavigationTracking();  // call in router root
```

### 4.2 Next.js Integration

Special handling needed:
- App Router (RSC): inject SDK in `app/layout.tsx` client component
- Pages Router: inject in `_app.tsx`
- Guard with `typeof window !== 'undefined'` to avoid server-side init
- Automatic route tracking via `usePathname()` / `useRouter()`

```typescript
// Next.js App Router
'use client';
import { PulseNextProvider } from '@dreamhorizon/pulse-web/nextjs';

export default function RootLayout({ children }) {
  return (
    <html>
      <body>
        <PulseNextProvider config={pulseConfig}>
          {children}
        </PulseNextProvider>
      </body>
    </html>
  );
}
```

### 4.3 Vue Plugin

```typescript
// Vue 3
app.use(PulseVuePlugin, { config: pulseConfig })
// Auto-installs global error handler + router integration
```

### 4.4 CDN / Vanilla JS

```html
<!-- Snippet-based init (like Segment, Amplitude, Datadog RUM) -->
<script>
  window.PulseWebSnippet = window.PulseWebSnippet || [];
  window.PulseWebSnippet.push(['start', { apiKey: 'pk_...', ... }]);
</script>
<script src="https://cdn.pulse.io/pulse-web@1.js" async></script>
```

---

## Phase 5 — Build & Distribution (Week 8)

### 5.1 Build Targets

Using **tsup** (esbuild-based, simpler for libraries) or **Rollup**:

```
dist/
├── pulse-web.esm.js       # ESM (tree-shakeable, for bundlers)
├── pulse-web.cjs.js       # CommonJS (Node.js SSR)
├── pulse-web.umd.js       # UMD (CDN, global window.PulseWeb)
├── pulse-web.umd.min.js   # Minified CDN build
├── types/                 # TypeScript declarations
└── integrations/
    ├── react.esm.js
    ├── nextjs.esm.js
    └── vue.esm.js
```

**Bundle size goals:**
- Core SDK (no replay): `< 30KB` gzipped
- With session replay (rrweb): `< 80KB` gzipped
- CDN async loader snippet: `< 1KB` (non-blocking)

### 5.2 npm Package

```json
{
  "name": "@dreamhorizon/pulse-web",
  "version": "0.1.0-alpha.1",
  "main": "dist/pulse-web.cjs.js",
  "module": "dist/pulse-web.esm.js",
  "types": "dist/types/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/pulse-web.esm.js",
      "require": "./dist/pulse-web.cjs.js"
    },
    "./react": { "import": "./dist/integrations/react.esm.js" },
    "./nextjs": { "import": "./dist/integrations/nextjs.esm.js" },
    "./vue":    { "import": "./dist/integrations/vue.esm.js" }
  },
  "sideEffects": false
}
```

### 5.3 CDN Distribution

- Upload to S3 + CloudFront with versioned paths: `cdn.pulse.io/pulse-web@1.2.3/`
- Immutable cache headers on versioned paths
- `pulse-web@1` → latest 1.x (mutable, short TTL)
- SRI (Subresource Integrity) hashes published alongside each release

### 5.4 CI/CD Pipeline (GitHub Actions)

```
PR opened      → lint + typecheck + unit tests + bundle size check
Merge to main  → integration tests + e2e (Playwright)
Release tag    → build + npm publish + CDN upload + GitHub release notes
```

---

## Phase 6 — Testing Strategy (Week 8–9)

| Layer | Tool | What's Tested |
|---|---|---|
| Unit | Vitest | Instrumentation logic, attribute extraction, session management |
| Integration | Vitest + JSDOM | Full SDK init, signal batching, exporter chain |
| E2E Browser | Playwright | Real browser: click tracking, network calls, error capture |
| E2E Replay | Playwright | Session replay recording + playback fidelity |
| Performance | Lighthouse CI | Bundle size regression, no render-blocking |
| Compatibility | BrowserStack | Chrome, Firefox, Safari, Edge, iOS Safari, Chrome Android |

---

## Phase 7 — Backend & UI Changes (Week 9–10)

The backend needs minimal changes because OTLP is already handled. But:

### What needs updating:

1. **ClickHouse schema** — Add `platform = 'web'` to allowed values in materialized columns (non-breaking)
2. **`pulse.type` additions** — `web_vital` is new; add to OTEL Collector routing rules if needed
3. **pulse-ui dashboards** — Update existing filters to include `platform = 'web'` (Platform filter already exists in the UI)
4. **Session Replay player** — Currently designed for screenshots (mobile). For web (rrweb), need an rrweb player component in the dashboard. This is significant UI work.
5. **Remote config** — Add web-specific feature flags (`web_vitals`, `session_replay_web`, etc.) to the config schema

---

## Feature Parity: Mobile vs Web

| Feature | Android | iOS | React Native | Web (planned) |
|---|---|---|---|---|
| JS/Native Crashes | ✅ | ✅ | ✅ | ✅ `window.onerror` |
| Network requests | ✅ | ✅ | ✅ | ✅ fetch + XHR |
| UI Clicks | ✅ | ✅ | ✅ | ✅ |
| Rage Clicks | ✅ | ✅ | — | ✅ |
| Dead Clicks | ✅ | ✅ | — | ✅ |
| Session Replay | ✅ | ✅ | — | ✅ rrweb (DOM) |
| Performance metrics | ✅ | ✅ | — | ✅ Web Vitals |
| Long tasks / Jank | ✅ | ✅ | — | ✅ PerformanceObserver |
| Interactions (user journeys) | ✅ | ✅ | — | ✅ pure TS port |
| Custom Events | ✅ | ✅ | ✅ | ✅ |
| User ID / Properties | ✅ | ✅ | ✅ | ✅ |
| Remote config | ✅ | ✅ | ✅ | ✅ |
| Offline buffering | ✅ disk | ✅ disk | — | ✅ localStorage |

---

## Complete Timeline Summary

| Phase | Scope | Duration |
|---|---|---|
| 1 | Foundation: init, session, exporters, resource | Week 1–2 |
| 2 | Auto-instrumentations: errors, network, clicks, vitals, navigation | Week 3–4 |
| 2.5 | Interactions: config fetch, sequence matching, APDEX span output | Week 4–5 |
| 3 | Session replay: rrweb, privacy, compression, transport | Week 6–7 |
| 4 | Framework integrations: React, Next.js, Vue, CDN snippet | Week 8 |
| 5 | Build system, npm publish, CDN, CI/CD | Week 9 |
| 6 | Testing: unit, e2e, browser compat | Week 9–10 |
| 7 | Backend/UI updates for web platform | Week 10–11 |

---

## Key Decisions to Make Before Starting

1. **Session Replay in v1 or v2?**
   rrweb adds ~50KB gzip. Recommend shipping as optional add-on (opt-in) to keep the default bundle lean.

2. **CDN-first or npm-first?**
   Modern apps with bundlers need npm. Marketing sites/legacy stacks need CDN snippet. Both should be supported, but npm-first is the right primary path.

3. **Separate repo or same monorepo?**
   Recommend same monorepo (consistent with Android/iOS) but published as a separate npm package.

4. **React Native Web overlap?**
   If customers use React Native Web, both the RN SDK and web SDK could initialize. Need a deduplication strategy or a universal entrypoint that detects the environment.

5. **Versioning cadence:**
   Start at `0.1.0-alpha` to allow breaking API changes before GA.

---

## Immediate Next Steps

1. Create `pulse-web-otel/` package scaffold (`package.json`, `tsconfig.json`, `rollup.config.ts`)
2. Wire up OTEL tracer provider → OTLP HTTP exporter and verify a span lands in ClickHouse
3. Define `PulseWebConfig` interface in `config.ts` mirroring the mobile API
4. Build `session.ts` (installation ID + session ID management in localStorage/sessionStorage)
5. Enable `@opentelemetry/instrumentation-fetch` and confirm network spans appear in the existing Pulse dashboard
6. Port `InteractionUtil.matchSequence()` to TypeScript and write unit tests against the same scenarios as mobile (sequence match, timeout, blacklist abort, all 6 property operators)
