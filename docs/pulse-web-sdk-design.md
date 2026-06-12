# Pulse Web SDK — End-to-End Design Document

**Architecture, Feature Specification & Implementation Plan**

| | |
|---|---|
| **Version** | 1.0 |
| **Date** | April 14, 2026 |
| **Status** | Proposal (superseded in part by shipped code — see note below) |
| **Author** | Shruti |

**Where the Web SDK lives today:** implementation and canonical contracts are in **`pulse-web-otel/`** (package **`@dreamhorizonorg/pulse-web`**). For **screen navigation** specifically, use **`pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`** and **`sdk-core/`** ([`SPEC.md`](../pulse-web-otel/docs/sdk-core/SPEC.md) + [`data-contract/SPEC.md`](../pulse-web-otel/docs/sdk-core/data-contract/SPEC.md)) — not every detail in §5.5 / §4.6 below matches the shipped design.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [SDK Identity & Platform Registration](#2-sdk-identity--platform-registration)
3. [Repository Structure](#3-repository-structure)
4. [Public API](#4-public-api)
5. [Feature Specification](#5-feature-specification)
6. [Architecture Deep Dive](#6-architecture-deep-dive)
7. [Framework Integration Packages](#7-framework-integration-packages)
8. [Types Reference](#8-types-reference)
9. [Backend Changes Required](#9-backend-changes-required)
10. [Implementation Phases](#10-implementation-phases)
11. [Privacy & Security](#11-privacy--security)
12. [Performance Budget](#12-performance-budget)
13. [Browser Support Matrix](#13-browser-support-matrix)
14. [Naming Conventions & Attribute Mapping](#14-naming-conventions--attribute-mapping)
15. [Open Questions & Decisions Required](#15-open-questions--decisions-required)

---

## 1. Executive Summary

Pulse currently ships native SDKs for Android (Kotlin), iOS (Swift), and React Native (TypeScript). This document specifies the design for **`pulse-web-js`** — a first-class browser SDK that brings Real User Monitoring (RUM) to web applications with full feature parity to the existing mobile SDKs.

The Web SDK will:

- Be built on **OpenTelemetry JS** (the same telemetry foundation used by the mobile SDKs)
- Export signals over **OTLP/HTTP** to the existing Pulse backend — **no backend changes required for core ingestion**
- Require minimal backend additions: a new `pulse_web_js` enum value and a `web` platform classifier
- Ship as a **tree-shakeable ES module** (`@pulse/web`) with an optional `<script>` CDN tag for non-bundler apps
- Mirror the exact public API surface of the mobile SDKs so the dashboard and backend treat it as a first-class platform

---

## 2. SDK Identity & Platform Registration

### 2.1 SDK Name

Register as `pulse_web_js` in the backend `Sdk` enum alongside:

```
pulse_android_java
pulse_android_rn
pulse_ios_swift
pulse_ios_rn
pulse_web_js   ← new
```

### 2.2 OpenTelemetry Resource Attributes

Every signal is stamped with the following resource attributes:

| Attribute | Value | Notes |
|---|---|---|
| `telemetry.sdk.name` | `pulse_web_js` | Identifies this SDK in backend |
| `telemetry.sdk.version` | e.g. `1.0.0` | SDK package version |
| `service.name` | User-provided app name | From `PulseWebConfig.appName` |
| `service.version` | User-provided app version | From `PulseWebConfig.appVersion` |
| `deployment.environment` | e.g. `production` | From config or auto-detected |
| `browser.user_agent` | `navigator.userAgent` | Auto-collected |
| `browser.language` | `navigator.language` | Auto-collected |
| `os.type` | `browser` | Static for web |
| `device.type` | `desktop` / `mobile` / `tablet` | Derived from UA + screen size |
| `device.screen.width` | `screen.width` | Auto-collected |
| `device.screen.height` | `screen.height` | Auto-collected |
| `device.screen.aspect_ratio` | e.g. `16:9` | Simplified, auto-derived |
| `app.installation.id` | Stable UUID | Persisted in `localStorage` |

---

## 3. Repository Structure

```
pulse-web-js/
├── package.json
├── tsconfig.json
├── rollup.config.ts              # Bundles ESM + CJS + IIFE (CDN)
├── src/
│   ├── index.ts                  # Public API barrel export
│   ├── PulseWeb.ts               # Singleton facade (mirrors PulseSDK / Pulse.shared)
│   ├── config/
│   │   ├── PulseWebConfig.ts     # Init config type
│   │   ├── RemoteConfigFetcher.ts # Fetches /v1/configs/active/, caches in sessionStorage
│   │   └── InstrumentationConfig.ts # Per-feature toggle type
│   ├── core/
│   │   ├── OtelSetup.ts          # WebTracerProvider + MeterProvider + LoggerProvider bootstrap
│   │   ├── OtlpExporter.ts       # Configures OTLP/HTTP exporters (spans, logs, metrics)
│   │   ├── ResourceBuilder.ts    # Builds OTel Resource with browser attributes
│   │   └── ConsentManager.ts     # PENDING / ALLOWED / DENIED buffer logic
│   ├── session/
│   │   ├── SessionManager.ts     # Generates session ID, tracks expiry, emits session events
│   │   ├── InstallationIdManager.ts # Persistent UUID in localStorage
│   │   └── UserSessionEmitter.ts # Emits user.session.start / end log events
│   ├── instrumentation/
│   │   ├── NetworkInstrumentation.ts    # Patches fetch + XHR, emits network spans
│   │   ├── ErrorInstrumentation.ts      # window.onerror + unhandledrejection
│   │   ├── NavigationInstrumentation.ts # History API (pushState/replaceState) + popstate — no hash-only router hook
│   │   ├── ClickInstrumentation.ts      # Click capture, rage-click detection
│   │   ├── WebVitalsInstrumentation.ts  # CLS, LCP, INP, FID, TTFB via web-vitals
│   │   ├── PageLifecycleInstrumentation.ts  # visibilitychange, beforeunload
│   │   └── ResourceTimingInstrumentation.ts # PerformanceObserver resource timing
│   ├── replay/
│   │   ├── SessionReplay.ts      # rrweb-based DOM replay capture
│   │   ├── ReplayPrivacy.ts      # CSS selector-based masking rules
│   │   └── ReplayUploader.ts     # Chunked blob upload to backend
│   ├── user/
│   │   └── UserManager.ts        # setUserId / setUserProperty / setUserProperties
│   ├── signals/
│   │   ├── EventTracker.ts       # trackEvent → OTLP log
│   │   ├── ErrorTracker.ts       # trackNonFatal → OTLP log
│   │   └── SpanTracker.ts        # trackSpan / startSpan → OTLP trace
│   └── util/
│       ├── uuid.ts               # crypto.randomUUID polyfill
│       ├── DeviceInfo.ts         # UA parsing, screen info
│       ├── Logger.ts             # Internal debug logger
│       └── BeforeSend.ts         # BeforeSend hook types
├── cdn/
│   └── pulse.min.js              # IIFE bundle for <script> tag usage
└── tests/
    ├── unit/
    └── integration/
```

---

## 4. Public API

The Web SDK mirrors the mobile SDK APIs exactly. All methods are available on the `Pulse` singleton.

### 4.1 Initialization

```typescript
import { Pulse } from '@pulse/web';

Pulse.init({
  endpointBaseUrl: 'https://your-pulse-backend.com',
  apiKey: 'projectId_suffix',
  appName: 'my-web-app',
  appVersion: '2.1.0',
  environment: 'production',
  dataCollectionState: 'ALLOWED',  // 'PENDING' | 'ALLOWED' | 'DENIED'

  // Optional: fine-grained feature toggles
  instrumentations: {
    network: true,
    errors: true,
    navigation: true,
    clicks: true,
    webVitals: true,
    pageLifecycle: true,
    sessionReplay: false,  // off by default, controlled via remote config
  },

  // Optional: global attributes stamped on every signal
  globalAttributes: {
    'app.region': 'us-east-1',
    'app.tenant': 'acme-corp',
  },

  // Optional: modify or drop signals before export
  beforeSend: {
    beforeSendSpan: (span) => span,    // return null to drop
    beforeSendLog: (log) => {
      // e.g. strip PII from URLs
      return log;
    },
    beforeSendMetric: (metric) => metric,
  },

  networkHeaders: {
    requestHeaders: ['x-request-id', 'x-trace-id'],
    responseHeaders: ['x-response-time'],
  },
});
```

### 4.2 Lifecycle

```typescript
Pulse.init(config: PulseWebConfig): void
Pulse.shutdown(): void
Pulse.isInitialized(): boolean
Pulse.setDataCollectionState(state: PulseDataCollectionConsent): void
```

### 4.3 User Identity

```typescript
Pulse.setUserId(id: string | null): void
Pulse.setUserProperty(name: string, value: string | number | boolean): void
Pulse.setUserProperties(properties: Record<string, string | number | boolean | null>): void
```

### 4.4 Event Tracking

```typescript
Pulse.trackEvent(
  name: string,
  attributes?: PulseAttributes,
  observedTimestampMs?: number
): void

Pulse.trackNonFatal(
  error: Error | string,
  attributes?: PulseAttributes,
  observedTimestampMs?: number
): void
```

### 4.5 Performance Spans

```typescript
// Wrap a synchronous or async block in a span
const result = await Pulse.trackSpan(
  'checkout-flow',
  async () => await processPayment(),
  { 'checkout.cart_size': 3 }
);

// Manual span lifecycle
const span = Pulse.startSpan('image-processing', { 'image.format': 'webp' });
span.addEvent('resize-complete', { 'image.size_kb': 450 });
span.setAttributes({ 'image.output_format': 'avif' });
span.recordException(new Error('compression failed'));
span.end();  // or span.end('ERROR')
```

### 4.6 Navigation Tracking

**Shipped (`pulse-web-otel`):** `NavigationInstrumentation` is installed with the SDK when the **`screen_navigation`** remote feature is on. SPA screen signals require **History API** updates (`pushState` / `replaceState` / `popstate`); hash-only URL changes without History do not emit **`screen_load` / `screen_session`**.

```typescript
// React Router v6 — set screen.name for global attrs; NavigationInstrumentation emits spans
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";

// Next.js App Router — same pattern from @dreamhorizonorg/pulse-web/next
// import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";

// Proposal-era names below (@pulse/web, usePulseNavigation) are not the shipped public API.
```

### 4.7 Session Replay

```typescript
// Controlled programmatically or via remote SDK config (feature flag: session_replay)
Pulse.startReplay();
Pulse.stopReplay();
Pulse.addReplayMask('.credit-card-number, [data-sensitive]');
Pulse.addReplayUnmask('.public-username');
```

### 4.8 CDN / Script Tag Usage

```html
<script src="https://cdn.pulse.io/sdk/web/1.0.0/pulse.min.js"></script>
<script>
  window.Pulse.init({
    endpointBaseUrl: 'https://your-pulse-backend.com',
    apiKey: 'projectId_suffix',
    appName: 'my-web-app',
    appVersion: '1.0.0',
  });
</script>
```

---

## 5. Feature Specification

### 5.1 Feature Parity Matrix

| Feature | Android | iOS | React Native | Web | Web Browser API |
|---|---|---|---|---|---|
| Custom event tracking | ✅ | ✅ | ✅ | ✅ | OTLP log |
| Non-fatal error tracking | ✅ | ✅ | ✅ | ✅ | OTLP log |
| Fatal crash / unhandled error | ✅ | ✅ | ✅ | ✅ | `window.onerror` + `unhandledrejection` |
| ANR / Long Task detection | ✅ | ❌ | ✅ | ✅ | `PerformanceObserver` (longtask) |
| Performance spans | ✅ | ✅ | ✅ | ✅ | OTLP trace |
| Network auto-instrumentation | ✅ | ✅ | ✅ | ✅ | `fetch` + `XHR` patch |
| Session lifecycle | ✅ | ✅ | ✅ | ✅ | `visibilitychange` + idle timeout |
| Page session spans | ✅ | ✅ | ✅ | ✅ | OTLP **span** → `otel_traces`; History API + `popstate` |
| Page load span | ❌ | ❌ | ✅ | ✅ | OTLP **span**; Navigation Timing + SPA marker (`start.type=spa`) |
| Page interactive span | ❌ | ❌ | ✅ | ⚠️ Web | **RN:** `screen_interactive` span. **Web:** no separate span — **`tti`** (when available) on **`screen_load`** span attrs (see screen-signals SPEC). |
| User identity | ✅ | ✅ | ✅ | ✅ | In-memory + sessionStorage |
| Global attributes | ✅ | ✅ | ✅ | ✅ | Merged on every signal |
| Installation ID | ✅ | ✅ | ✅ | ✅ | `localStorage` UUID |
| Remote SDK config | ✅ | ✅ | ✅ | ✅ | `sessionStorage` cache |
| Consent management | ✅ | ✅ | ✅ | ✅ | In-memory ring buffer |
| BeforeSend hook | ✅ | ✅ | ❌ | ✅ | OTel Processor pipeline |
| Session replay | ✅ | ✅ | ❌ | ✅ | rrweb DOM recording |
| Click / tap tracking | ✅ | ✅ | ❌ | ✅ | `addEventListener('click')` |
| Rage click detection | ✅ | ✅ | ❌ | ✅ | Click frequency + position |
| **Web Vitals (LCP, CLS, INP)** | ❌ | ❌ | ❌ | ✅ | `web-vitals` library |
| **Long Task monitoring** | ❌ | ❌ | ❌ | ✅ | `PerformanceObserver` |
| **Resource timing** | ❌ | ❌ | ❌ | ✅ | `PerformanceObserver` |
| Frame drop / jank | ✅ | ✅ | ✅ | ✅ | `requestAnimationFrame` loop |
| Network connectivity change | ✅ | ✅ | ✅ | ✅ | `navigator.onLine` + events |
| App / page lifecycle | ✅ | ✅ | ✅ | ✅ | Page Visibility API |
| React integration | ❌ | ❌ | ✅ | ✅ | ErrorBoundary + hooks |
| Vue integration | ❌ | ❌ | ❌ | ✅ | Plugin + `app.config.errorHandler` |
| Angular integration | ❌ | ❌ | ❌ | ✅ | `ErrorHandler` service |
| Next.js integration | ❌ | ❌ | ❌ | ✅ | Provider + route events |
| Offline buffering | ✅ | ✅ | ✅ | ✅ | IndexedDB queue |
| Source map symbolication | ✅ | ✅ | ✅ | ✅ | Source map upload CLI |

> **Bold rows** = web-exclusive features with no mobile equivalent.

---

### 5.2 Session Management

**Session ID generation:** `crypto.randomUUID()` with polyfill for older browsers.

**Storage:**
- `sessionStorage` — `session.id` (cleared on tab close, matching mobile "one session per app instance")
- `localStorage` — `app.installation.id` (permanent, survives tab close)

**Session expiry rules (matching mobile):**

| Rule | Value |
|---|---|
| Inactivity timeout | 30 minutes |
| Max lifetime | 4 hours |
| Tab close (`beforeunload`) | Emit `session.end` via `navigator.sendBeacon` |
| Tab hidden + returns after inactivity | New session |
| Multiple tabs | Each tab has own `session.id`; `installation.id` shared |

**Session lifecycle events** emitted as OTLP log records:

```
session.start  →  pulse.type = session.start
  attributes: session.id, app.installation.id, user.id (if set)

session.end    →  pulse.type = session.end
  attributes: pulse.session.crash.count, pulse.session.non_fatal.count,
              pulse.session.long_task.count, pulse.metering.session.id
```

---

### 5.3 Network Instrumentation

Patches both `fetch` and `XMLHttpRequest` to emit OTLP spans per request.

**Captured attributes:**

| Attribute | Source |
|---|---|
| `http.method` | Request method |
| `http.url` | Full URL (query params stripped by default) |
| `http.host` | Hostname |
| `http.status_code` | Response status |
| `http.request_content_length` | Request body size |
| `http.response_content_length` | Response body size |
| `http.duration_ms` | End-to-end latency |
| `http.error` | Error message if failed |
| Configured request/response headers | Via `networkHeaders` config |

**Privacy defaults:**
- Query string parameters stripped by default (opt-in via `captureQueryParams: true`)
- POST body never captured
- `Authorization` / `Cookie` headers blocked by default; allowlist-only via `networkHeaders`
- SDK endpoint excluded from instrumentation (no self-referential loops)

---

### 5.4 Error Instrumentation

```javascript
// Unhandled JS errors → pulse.type = device.crash (isFatal: true)
window.onerror = (message, source, lineno, colno, error) => { ... }

// Unhandled promise rejections → pulse.type = device.crash (isFatal: true)
window.addEventListener('unhandledrejection', (event) => { ... })

// Manual non-fatal → pulse.type = non_fatal
Pulse.trackNonFatal(error, { 'component': 'checkout-form' });
```

**React Error Boundary:**

```jsx
import { PulseErrorBoundary } from '@pulse/web/react';

<PulseErrorBoundary fallback={<ErrorPage />} onError={(e) => console.error(e)}>
  <App />
</PulseErrorBoundary>
```

**Stack trace symbolication:** Raw stacks captured and uploaded as-is. Source map resolution happens server-side (same lazy/on-query approach as Android ProGuard / iOS dSYM). Source maps uploaded via Pulse CLI during CI/CD.

---

### 5.5 Navigation / Page Tracking

**Shipped web model (`pulse-web-otel`):** two OTLP **client spans** (not log records) exported to **`otel_traces`**, span **names** literals `screen_load` and `screen_session` (not the route string). See **`pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`**.

**Page Session Span** (`pulse.type = screen_session`, **Span**)
```
starts: after each screen_load (cold or SPA), for dwell time
ends:   next route change, pagehide, uninstall, or instrumentation shutdown
attrs:  applied at span end — screen.name, session.duration_ms, url.path snapshot for exited screen, etc.
```

**Page Load Span** (`pulse.type = screen_load`, **Span**)
```
cold:   Navigation Timing–anchored start/end; attrs include tti, ttfb, start.type (cold|reload|back_forward), navigation.type when available
spa:    marker span on History transition; start.type = spa; screen.name from URL + routePatterns (resolveScreenNameFromUrl)
```

**Page Interactive (`pulse.type = screen_interactive`) — web vs mobile**
- **React Native** may emit a dedicated **`screen_interactive`** span (`markContentReady()`-style lifecycle).
- **Web:** no separate **`screen_interactive`** span; interactivity signal is **`tti`** (and related timing attrs) on **`screen_load`** when Navigation Timing allows.

**Initial Page Load** uses `PerformanceNavigationTiming` (where available) for cold **`screen_load`** span timing and attributes.

---

### 5.6 Web Vitals (Web-Exclusive)

Collected via the `web-vitals` library. Emitted as **both** an OTLP Gauge metric (for dashboard aggregation) and an OTLP log record (for per-session correlation).

| Metric | `pulse.type` | Description |
|---|---|---|
| LCP (Largest Contentful Paint) | `web_vital.lcp` | Render time of largest visible element |
| CLS (Cumulative Layout Shift) | `web_vital.cls` | Total unexpected layout shift score |
| INP (Interaction to Next Paint) | `web_vital.inp` | Interaction → next paint latency |
| FCP (First Contentful Paint) | `web_vital.fcp` | Time to first content painted |
| TTFB (Time to First Byte) | `web_vital.ttfb` | Server response time |

All vitals include `session.id` and `screen.name` attributes for per-session / per-page drilldown.

---

### 5.7 Click & Rage-Click Tracking

```
listener: document.addEventListener('click', handler, { capture: true })

emits OTLP log:
  pulse.type = app.click
  click.type = 'good' | 'dead'
  click.is_rage = true | false
  click.rage_count = N
  app.screen.coordinate.nx = clientX / innerWidth   (normalized 0–1)
  app.screen.coordinate.ny = clientY / innerHeight  (normalized 0–1)
  app.click.context = tag + aria-label (no inner text, no IDs)
```

- **Dead click:** click produces no DOM change, navigation, or network request within 500ms
- **Rage click:** 3+ clicks within 400ms within a 40px radius

---

### 5.8 Long Task Detection (Web ANR Equivalent)

```javascript
const observer = new PerformanceObserver((list) => {
  for (const entry of list.getEntries()) {
    if (entry.duration >= 50) {
      // emit pulse.type = device.anr
      // attrs: anr.duration_ms, anr.start_time_ms, screen.name
    }
  }
});
observer.observe({ entryTypes: ['longtask'] });
```

Tasks blocking the main thread for ≥50ms (RAIL model threshold) map to `pulse.type = device.anr`, keeping the backend schema consistent with mobile ANRs.

---

### 5.9 Session Replay

Built on **rrweb** — the industry standard used by Sentry, PostHog, Highlight, and Datadog.

**Pipeline:**
```
rrweb record() → DOM mutation events
    → compress with pako (gzip)
    → chunk into blobs (max 500KB)
    → upload to session replay ingestion endpoint
    → navigator.sendBeacon fallback on tab close
```

**Privacy controls:**

| Control | Default |
|---|---|
| `<input>`, `<textarea>`, `<select>` content | Masked |
| `[data-pulse-mask]` / `.pulse-mask` subtree | Masked |
| `[data-pulse-unmask]` / `.pulse-unmask` | Force-unmasked |
| Images | Replaced with placeholder rectangle |
| `textAndInputPrivacy` | `MASK_ALL` (mirrors iOS enum) |

**Sampling:** Gated by remote SDK config `sessionSampleRate` on the `session_replay` feature flag — same mechanism as iOS.

---

### 5.10 Offline / Disk Buffering

When network is unavailable or backend returns 5xx:

- Signals queued in **IndexedDB** — persistent, survives tab close
- Queue depth: **5000 signals per type** (matches mobile SDKs)
- On reconnect (`online` event): retry with exponential backoff — 1s → 2s → 4s → 8s, max 5 retries
- Entries older than **24 hours** are pruned automatically

**Consent `PENDING` mode:** signals buffered **in-memory** (not IndexedDB). On `ALLOWED` → flush. On `DENIED` → discard + teardown all instrumentation.

---

### 5.11 Remote SDK Config

On `Pulse.init()`:
1. Check `sessionStorage` for cached `PulseSdkConfig`
2. If stale/absent → `GET {baseUrl}/v1/configs/active/` with `X-API-KEY` header
3. Apply feature flags — gates individual instrumentations by `pulse_web_js` SDK targeting
4. Background-refresh for next page load

---

### 5.12 BeforeSend Hook

```typescript
Pulse.init({
  beforeSend: {
    beforeSendSpan: (span: PulseSpanData) => {
      // Strip PII from URLs — return null to drop entirely
      if (span.attributes['http.url']?.includes('/user/')) {
        span.attributes['http.url'] = '/user/[redacted]';
      }
      return span;
    },
    beforeSendLog: (log: PulseLogData) => {
      if (log.body === 'health-check') return null;  // drop
      return log;
    },
    beforeSendMetric: (metric: PulseMetricData) => metric,
  }
});
```

Implemented as OpenTelemetry Span/Log Processors inserted before the batch exporter.

---

## 6. Architecture Deep Dive

### 6.1 OpenTelemetry Foundation

| OTel Package | Purpose |
|---|---|
| `@opentelemetry/sdk-trace-web` | `WebTracerProvider` for span creation |
| `@opentelemetry/sdk-logs` | `LoggerProvider` for log records |
| `@opentelemetry/sdk-metrics` | `MeterProvider` for web vitals + counters |
| `@opentelemetry/exporter-trace-otlp-http` | Export spans → `POST /v1/traces` |
| `@opentelemetry/exporter-logs-otlp-http` | Export logs → `POST /v1/logs` |
| `@opentelemetry/exporter-metrics-otlp-http` | Export metrics → `POST /v1/metrics` |
| `@opentelemetry/resources` | Static resource attribute builder |
| `@opentelemetry/propagator-b3` | W3C TraceContext + B3 propagation |
| `@opentelemetry/instrumentation-fetch` | Base fetch instrumentation (extended) |
| `@opentelemetry/instrumentation-xml-http-request` | Base XHR instrumentation (extended) |
| `@opentelemetry/context-zone` | Zone.js context propagation (Angular) |

Wire format: **OTLP/HTTP JSON** for v1 (browsers have no native protobuf; avoids a codec dependency).

### 6.2 Signal Routing

```
                 ┌──────────────────────────┐
                 │       Pulse.init()        │
                 │  WebTracerProvider        │
                 │  LoggerProvider           │
                 │  MeterProvider            │
                 └────────────┬─────────────┘
                              │
         ┌────────────────────┼─────────────────────┐
         ▼                    ▼                      ▼
  OTLP Span Exporter   OTLP Log Exporter    OTLP Metric Exporter
  POST /v1/traces      POST /v1/logs        POST /v1/metrics
  ─────────────────    ──────────────────   ────────────────────
  Network spans        Custom events        Web Vitals
  Page load/session    Errors / crashes     Frame counters
  Custom spans         Clicks
                       Session lifecycle
```

Custom events route to `customEventCollectorUrl` when set in remote config (matching mobile behavior).

### 6.3 Consent State Machine

```
┌─────────────────────────────────────────────┐
│                  PENDING                    │
│  Buffer in-memory ring (max 5000/type)      │
└────────────────────┬────────────────────────┘
                     │ setDataCollectionState(ALLOWED)
                     ▼
┌─────────────────────────────────────────────┐
│                  ALLOWED                    │
│  Flush buffer → normal OTLP export          │
└────────────────────┬────────────────────────┘
                     │ setDataCollectionState(DENIED)
                     ▼
┌─────────────────────────────────────────────┐
│                  DENIED                     │
│  Clear buffer + teardown all instrumentations│
│  All localStorage / sessionStorage purged   │
└─────────────────────────────────────────────┘
```

### 6.4 Export Pipeline with BeforeSend

```
Instrumentation produces signal
        ↓
BeforeSend Processor  ← user hook (modify or return null to drop)
        ↓
ConsentProcessor      ← drops if DENIED, buffers if PENDING
        ↓
BatchSpanProcessor / BatchLogRecordProcessor / PeriodicExportingMetricReader
        ↓
OTLP/HTTP Exporter → Pulse Backend
```

---

## 7. Framework Integration Packages

Core `@pulse/web` is framework-agnostic. Optional sub-packages provide idiomatic integrations:

### 7.1 `@pulse/web/react`

```typescript
import { PulseErrorBoundary, withPulseErrorBoundary, usePulseNavigation,
         PulseMask, PulseUnmask } from '@pulse/web/react';

function App() {
  usePulseNavigation();  // React Router v6 auto-tracking
  return (
    <PulseErrorBoundary fallback={<ErrorFallback />}>
      <Router>
        <PulseMask>
          <CreditCardForm />  {/* masked in replay */}
        </PulseMask>
      </Router>
    </PulseErrorBoundary>
  );
}
```

### 7.2 `@pulse/web/next`

```typescript
// app/layout.tsx (App Router)
import { PulseNextjsProvider } from '@pulse/web/next';

export default function RootLayout({ children }) {
  return (
    <PulseNextjsProvider config={{
      endpointBaseUrl: process.env.NEXT_PUBLIC_PULSE_ENDPOINT,
      apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY,
      appName: 'my-next-app',
      appVersion: process.env.NEXT_PUBLIC_APP_VERSION,
    }}>
      {children}
    </PulseNextjsProvider>
  );
}
// Hooks into Next.js router events automatically — supports both App Router and Pages Router
```

### 7.3 `@pulse/web/vue`

```typescript
// main.ts
import { createPulsePlugin } from '@pulse/web/vue';
app.use(createPulsePlugin({ config: { /* PulseWebConfig */ }, router }));
```

### 7.4 `@pulse/web/angular`

```typescript
// app.config.ts (standalone)
import { providePulse } from '@pulse/web/angular';

bootstrapApplication(AppComponent, {
  providers: [
    providePulse({
      endpointBaseUrl: environment.pulseEndpoint,
      apiKey: environment.pulseApiKey,
      appName: 'my-angular-app',
      appVersion: '1.0.0',
    }),
  ],
});
// Provides PulseService injectable + Angular ErrorHandler + Zone.js context propagation
```

---

## 8. Types Reference

### 8.1 `PulseWebConfig`

```typescript
interface PulseWebConfig {
  // Required
  endpointBaseUrl: string;
  apiKey: string;

  // App metadata
  appName?: string;
  appVersion?: string;
  environment?: string;

  // Consent (default: 'ALLOWED')
  dataCollectionState?: PulseDataCollectionConsent;

  // Feature toggles — overridden by remote config
  instrumentations?: {
    network?: boolean;          // default: true
    errors?: boolean;           // default: true
    navigation?: boolean;       // default: true
    clicks?: boolean;           // default: true
    webVitals?: boolean;        // default: true
    pageLifecycle?: boolean;    // default: true
    longTasks?: boolean;        // default: true
    sessionReplay?: boolean;    // default: false
    resourceTiming?: boolean;   // default: false
  };

  networkHeaders?: {
    requestHeaders?: string[];
    responseHeaders?: string[];
  };

  networkIgnoreUrls?: Array<string | RegExp>;

  globalAttributes?: Record<string, string | number | boolean>;

  customEventCollectorUrl?: string;
  configEndpointUrl?: string;

  beforeSend?: PulseBeforeSendData;

  replayPrivacy?: {
    textAndInputPrivacy?: 'MASK_ALL' | 'MASK_SENSITIVE_INPUTS' | 'ALLOW_ALL';
    maskSelectors?: string[];
    unmaskSelectors?: string[];
    blockSelectors?: string[];  // completely excluded from replay
    maskImages?: boolean;
  };

  sessionConfig?: {
    maxLifetimeMs?: number;         // default: 4 hours
    inactivityTimeoutMs?: number;   // default: 30 minutes
  };

  debug?: boolean;
}
```

### 8.2 `PulseDataCollectionConsent`

```typescript
type PulseDataCollectionConsent = 'PENDING' | 'ALLOWED' | 'DENIED';
```

### 8.3 `PulseAttributes`

```typescript
type PulseAttributeValue = string | number | boolean | null;
type PulseAttributes = Record<string, PulseAttributeValue>;
```

### 8.4 `PulseSpan`

```typescript
interface PulseSpan {
  end(statusCode?: 'OK' | 'ERROR'): void;
  addEvent(name: string, attributes?: PulseAttributes): void;
  setAttributes(attributes: PulseAttributes): void;
  recordException(error: Error, attributes?: PulseAttributes): void;
  discard(): void;
  readonly spanId: string;
}
```

### 8.5 `PulseBeforeSendData`

```typescript
interface PulseBeforeSendData {
  beforeSend?: (data: PulseSignalData) => PulseSignalData | null | undefined;
  beforeSendSpan?: (data: PulseSpanData) => PulseSpanData | null | undefined;
  beforeSendLog?: (data: PulseLogData) => PulseLogData | null | undefined;
  beforeSendMetric?: (data: PulseMetricData) => PulseMetricData | null | undefined;
}
```

---

## 9. Backend Changes Required

The Web SDK uses the existing OTLP ingest pipeline without modification. Only these additions are needed:

### 9.1 Add `pulse_web_js` to `Sdk` Enum

In `backend/server/.../Sdk.java` (wherever the existing SDK enum is defined):

```java
pulse_web_js
```

Enables remote SDK config to target web-only feature flags.

### 9.2 Add `web` to Platform Classifier

In the ClickHouse materialized `Platform` column extraction logic:

```
telemetry.sdk.name = 'pulse_web_js'  →  Platform = 'web'
```

Web sessions appear alongside `android` and `ios` in the Pulse dashboard.

### 9.3 Source Map Upload Endpoint (New)

```
POST /v1/symbolicate/sourcemaps
  Content-Type: multipart/form-data
  X-API-KEY: {apiKey}
  Body: { file: .js.map, release: appVersion, service: appName }
```

Used by the Pulse CLI during CI/CD to upload source maps for server-side JS stack trace symbolication.

### 9.4 Pulse CLI — `sourcemaps upload` Command

```bash
npx @pulse/cli sourcemaps upload \
  --endpoint https://your-pulse-backend.com \
  --api-key projectId_suffix \
  --release 2.1.0 \
  --service my-web-app \
  ./dist/**/*.js.map
```

---

## 10. Implementation Phases

### Phase 1 — Core SDK Foundation (Weeks 1–3)

- [ ] Repo setup: `pulse-web-js/`, TypeScript, Rollup, ESM + CJS + IIFE outputs
- [ ] OTel provider bootstrap: `WebTracerProvider`, `LoggerProvider`, `MeterProvider`
- [ ] OTLP/HTTP exporters for spans, logs, metrics
- [ ] Resource builder with all browser attributes + UA parsing
- [ ] Singleton `PulseWeb` facade: `init()`, `shutdown()`, `isInitialized()`
- [ ] `X-API-KEY` header injection on all export requests
- [ ] Remote SDK config fetch + `sessionStorage` caching
- [ ] Consent manager: PENDING / ALLOWED / DENIED + ring buffer

### Phase 2 — Session & Identity (Week 4)

- [ ] `InstallationIdManager`: `localStorage` UUID + `pulse.app.installation.start` event
- [ ] `SessionManager`: UUID generation, idle + max lifetime expiry, tab visibility
- [ ] Session lifecycle events: `session.start` / `session.end` log records
- [ ] `UserManager`: `setUserId`, `setUserProperty`, `setUserProperties`, user session events
- [ ] Global attributes merged on every outgoing signal

### Phase 3 — Auto-Instrumentations (Weeks 5–7)

- [ ] `NetworkInstrumentation`: fetch + XHR patching, span per request, URL filtering, header capture
- [ ] `ErrorInstrumentation`: `window.onerror`, `unhandledrejection`, stack trace capture
- [ ] `NavigationInstrumentation`: History API patch + `popstate`; **`screen_load` + `screen_session`** OTLP spans only (no web **`screen_interactive`** span)
- [ ] `ClickInstrumentation`: click capture, rage-click detection, normalized coordinates
- [ ] `PageLifecycleInstrumentation`: `visibilitychange`, `beforeunload`, `online`/`offline`
- [ ] `WebVitalsInstrumentation`: LCP, CLS, INP, FCP, TTFB via `web-vitals`
- [ ] `LongTaskInstrumentation`: `PerformanceObserver` longtask API

### Phase 4 — Manual Tracking APIs (Week 7)

- [ ] `trackEvent()` → OTLP log (`pulse.type = custom_event`)
- [ ] `trackNonFatal()` → OTLP log (`pulse.type = non_fatal`)
- [ ] `trackSpan()` (sync + async) → OTLP trace span
- [ ] `startSpan()` → `PulseSpan` handle with `addEvent`, `setAttributes`, `recordException`, `discard`
- [ ] `BeforeSend` processor pipeline

### Phase 5 — Framework Integrations (Weeks 8–9)

- [ ] `@pulse/web/react` (shipped: `@dreamhorizonorg/pulse-web/react`): `PulseProvider`, `PulseErrorBoundary`, **`PulseRouterEvents` / `useRouterTracking`** (`/react/router`), etc.
- [ ] `@pulse/web/next`: `PulseNextjsProvider` (App Router + Pages Router)
- [ ] `@pulse/web/vue`: `createPulsePlugin` with Vue Router integration
- [ ] `@pulse/web/angular`: `providePulse()`, `PulseService`, Angular `ErrorHandler`

### Phase 6 — Session Replay (Weeks 10–11)

- [ ] Integrate `rrweb` record
- [ ] Privacy masking: CSS selector blocklist, `data-pulse-mask` / `data-pulse-unmask`
- [ ] Chunked compressed upload to session replay ingestion endpoint
- [ ] `navigator.sendBeacon` fallback on tab close
- [ ] Sampling gate via remote SDK config `session_replay` feature flag

### Phase 7 — Offline Buffering & Reliability (Week 12)

- [ ] IndexedDB queue for offline signal persistence
- [ ] Exponential backoff retry on network failure
- [ ] `navigator.sendBeacon` for `session.end` on `beforeunload`
- [ ] Auto-prune entries older than 24 hours

### Phase 8 — Backend Additions & Source Maps (Weeks 13–14)

- [ ] Add `pulse_web_js` to backend `Sdk` enum
- [ ] Add `web` to `Platform` classifier in ClickHouse materialization
- [ ] `POST /v1/symbolicate/sourcemaps` endpoint
- [ ] Pulse CLI `sourcemaps upload` command
- [ ] Server-side JS stack trace symbolication (lazy / on-query)

### Phase 9 — Testing, Docs & Release (Weeks 15–16)

- [ ] Unit tests for all core modules (Vitest)
- [ ] Integration tests against local Pulse backend
- [ ] Browser compatibility testing (Chrome, Firefox, Safari, Edge — last 2 major versions)
- [ ] CDN bundle (`cdn/pulse.min.js`) via Rollup IIFE build
- [ ] npm publish: `@pulse/web`, `@pulse/web/react`, `@pulse/web/next`, `@pulse/web/vue`, `@pulse/web/angular`
- [ ] Dashboard: add "Web" as platform filter in sessions / events views

---

## 11. Privacy & Security

### 11.1 Data Minimization Defaults

| Data | Default | Configurable |
|---|---|---|
| Full URL including query params | Stripped (path only) | `captureQueryParams: true` |
| Request / response bodies | Never captured | Not configurable |
| `Authorization` / `Cookie` headers | Blocked | `allowedRequestHeaders` allowlist only |
| Click target inner text | Not captured | `captureClickText: true` |
| DOM content in replay | Input values masked | `replayPrivacy.textAndInputPrivacy` |
| User IP address | Not captured by SDK | Backend may log at network layer |
| Geolocation | Never captured | Not available |

### 11.2 Cross-Origin Isolation

- `fetch` / XHR patches capture same-origin + explicitly allowlisted origins only
- No content captured from cross-origin iframes
- `document.referrer` captured but query params stripped

### 11.3 Content Security Policy Compatibility

The SDK uses no `eval()` / `new Function()`. Required CSP addition:

```
connect-src https://your-pulse-backend.com;
```

No inline script injection; no dynamic code execution.

### 11.4 GDPR / CCPA Compliance

- `dataCollectionState: 'PENDING'` → deferred consent (GDPR-compliant pre-consent buffering)
- `setDataCollectionState('DENIED')` → immediately stops collection, purges `localStorage` / `sessionStorage` / `IndexedDB`

```typescript
// Integration with a Consent Management Platform (CMP):
onConsentGranted(() => Pulse.setDataCollectionState('ALLOWED'));
onConsentRevoked(() => Pulse.setDataCollectionState('DENIED'));
```

---

## 12. Performance Budget

| Metric | Target |
|---|---|
| Bundle size — core (gzipped) | < 30 KB |
| Bundle size — with session replay (gzipped) | < 80 KB |
| Init time impact on main thread | < 10 ms |
| Memory overhead | < 5 MB baseline |
| Network overhead per session | < 50 KB/hr |
| Export batch interval | 5 seconds (configurable via remote config) |

All export uses `BatchSpanProcessor` + `BatchLogRecordProcessor` to coalesce signals into batches, minimizing HTTP request count.

---

## 13. Browser Support Matrix

| Browser | Minimum Version | Notes |
|---|---|---|
| Chrome | 80+ | All features supported |
| Firefox | 75+ | All features supported |
| Safari | 13.1+ | INP web vital not available |
| Edge | 80+ | All features (Chromium-based) |
| iOS Safari | 13.4+ | Some Web Vitals unavailable |
| Samsung Internet | 12+ | All features supported |
| IE 11 | Not supported | — |

**Graceful degradation:** If a browser API is unavailable (e.g., `PerformanceObserver` for longtasks), the feature silently skips — the SDK never throws.

---

## 14. Naming Conventions & Attribute Mapping

All attribute keys match existing Pulse conventions so the backend and dashboard work without schema changes:

| Web SDK Concept | Attribute Key | Mobile Equivalent |
|---|---|---|
| Current page / route | `screen.name` | `screen.name` |
| Previous page / route | `last.screen.name` | `last.screen.name` |
| Page session span | `pulse.type = screen_session` (**Span** → traces) | `pulse.type = screen_session` |
| Page load span | `pulse.type = screen_load` (**Span** → traces) | `pulse.type = screen_load` (RN) |
| Page interactive span | **Web:** no dedicated span — use **`tti`** on **`screen_load`**. **RN:** `pulse.type = screen_interactive` | `pulse.type = screen_interactive` (RN) |
| Long task (ANR equiv.) | `pulse.type = device.anr` | `pulse.type = device.anr` |
| Fatal JS error | `pulse.type = device.crash` | `pulse.type = device.crash` |
| Non-fatal error | `pulse.type = non_fatal` | `pulse.type = non_fatal` |
| Custom event | `pulse.type = custom_event` | `pulse.type = custom_event` |
| Click | `pulse.type = app.click` | `pulse.type = app.click` |
| Session start | `pulse.type = session.start` | `pulse.type = session.start` |
| Session end | `pulse.type = session.end` | `pulse.type = session.end` |
| Install event | `pulse.type = pulse.app.installation.start` | Same on Android / iOS |
| Network change | `pulse.type = network.change` | `pulse.type = network.change` |
| Web vitals (new) | `pulse.type = web_vital.*` | No mobile equivalent |

---

## 15. Open Questions & Decisions Required

| # | Question | Options | Recommendation |
|---|---|---|---|
| 1 | Session ID scope | Per-tab vs. shared across tabs | **Per-tab** (`sessionStorage`) — mirrors mobile "one session per app instance" |
| 2 | OTLP wire format | JSON vs. Protobuf | **JSON for v1** — no native protobuf in browsers; avoids codec dep |
| 3 | Session replay library | rrweb vs. OpenReplay vs. custom | **rrweb** — most widely adopted (Sentry, PostHog, Highlight all use it) |
| 4 | Web Vitals signal type | OTel Metric only vs. Log only vs. both | **Both** — gauge for aggregation + log for per-session correlation |
| 5 | Source map symbolication timing | On ingest vs. on query (lazy) | **On query (lazy)** — same as Android dSYM; avoids blocking ingest |
| 6 | CDN hosting | Self-hosted vs. third-party | **Third-party CDN** (Cloudflare / jsDelivr) with SRI hash in docs |
| 7 | Dead click definition | No DOM change in 500ms vs. no navigation | **No DOM change OR network request OR scroll in 500ms** — matches Datadog |
| 8 | Angular context propagation | Zone.js vs. async_hooks alternative | **`@opentelemetry/context-zone`** — full async context in Angular |
