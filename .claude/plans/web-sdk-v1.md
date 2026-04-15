# Pulse Web SDK — Complete V1 Implementation Plan

## Context

`pulse-web-otel/` does not exist yet. The goal is to bring the Android/iOS Pulse observability experience to web browsers. One line from the customer; Pulse captures sessions, errors, network, clicks, web vitals, interactions, and SPA navigation over the same OTLP pipeline already in production.

This plan covers the full V1 path: scaffold → M1 Foundation → M2 Interactions+Config+React → M3 Auto-Instrumentations → M4 Build+Frameworks+Ship, plus a React/Vite ecommerce demo app that serves as the manual verification harness for every milestone.

**Sources:**
- `pulse-web-otel/web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` — data contract and file map
- `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` — exit gates per milestone
- `pulse-web-otel/web-sdk-plan/v1/01-foundation/` through `v1/05-build-distribution/` — per-file specs
- `.cursor/plans/web_sdk_+_demo_app_0c273e46.plan.md` — demo app requirements

---

## Phase 0 — Monorepo Scaffold + Demo App Harness

### Files to create

```
pulse-web-otel/
├── package.json              # name: @dreamhorizon/pulse-web, workspaces: ["examples/*"]
├── tsconfig.json
├── tsup.config.ts            # ESM + CJS + types for src/index.ts entry
├── vitest.config.ts
├── .yarnrc.yml               # Yarn Berry config
├── README.md
├── src/
│   ├── index.ts              # Public exports (stubs initially)
│   ├── config.ts             # PulseWebConfig + PulseDataCollectionConsent
│   ├── sdk.ts                # PulseWebSDK singleton (stubs initially)
│   ├── session.ts            # placeholder
│   ├── resource.ts           # placeholder
│   ├── exporters.ts          # placeholder
│   ├── consent.ts            # placeholder
│   ├── remote-config.ts      # placeholder
│   ├── feature-gate.ts       # placeholder
│   ├── instrumentation-registry.ts  # placeholder
│   ├── version.ts            # __SDK_VERSION__ placeholder
│   ├── utils/
│   │   ├── ua-parser.ts      # placeholder
│   │   └── compression.ts    # placeholder
│   ├── instrumentations/     # empty dir
│   ├── processors/           # empty dir
│   ├── persistence/          # empty dir
│   └── integrations/         # empty dir
└── examples/
    └── ecommerce-demo/
        ├── package.json      # @dreamhorizon/pulse-web: "workspace:*"
        ├── vite.config.ts    # port 3002, /api proxy
        ├── tsconfig.json
        ├── index.html
        ├── src/
        │   ├── main.tsx
        │   ├── App.tsx       # PulseProvider wrap + React Router
        │   ├── routes/
        │   │   ├── Home.tsx
        │   │   ├── Products.tsx
        │   │   ├── ProductDetail.tsx
        │   │   ├── Cart.tsx
        │   │   ├── Checkout.tsx
        │   │   └── ErrorDemo.tsx   # throw render error + manual non_fatal
        │   ├── hooks/
        │   │   ├── useCart.ts
        │   │   └── useProducts.ts  # fetch /api/products.json
        │   └── components/
        │       ├── ProductCard.tsx  # clicks target
        │       └── RageClickButton.tsx  # 3 rapid clicks scenario
        └── public/
            ├── api/
            │   ├── products.json
            │   └── product-detail.json
            └── interaction-config.json  # checkout step config for M2
```

### Key `package.json` fields (SDK root)
```json
{
  "name": "@dreamhorizon/pulse-web",
  "version": "0.1.0-alpha.1",
  "type": "module",
  "main": "dist/index.cjs",
  "module": "dist/index.js",
  "types": "dist/index.d.ts",
  "exports": {
    ".": { "import": "./dist/index.js", "require": "./dist/index.cjs" }
  },
  "workspaces": ["examples/*"],
  "packageManager": "yarn@4.x"
}
```

### `config.ts` — full `PulseWebConfig` interface (from scaffold.md)
```typescript
export interface PulseWebConfig {
  endpointBaseUrl: string;
  apiKey: string;
  serviceName: string;
  serviceVersion?: string;
  dataCollectionState?: PulseDataCollectionConsent;
  beforeSend?: (signal: unknown) => unknown | null;
  globalAttributes?: Record<string, string | number | boolean>;
  configEndpointUrl?: string;
  export?: {
    format?: 'json' | 'protobuf';
    compression?: 'gzip' | 'none';
    batch?: { scheduledDelayMillis?: number; maxQueueSize?: number; maxExportBatchSize?: number };
  };
  diskBuffering?: { enabled?: boolean; maxSizeBytes?: number; maxAgeMs?: number };
  instrumentations?: InstrumentationConfig;
}
export enum PulseDataCollectionConsent { ALLOWED = 'ALLOWED', DENIED = 'DENIED', PENDING = 'PENDING' }
```

### Demo app routes
| Route | Purpose |
|---|---|
| `/` | Home — session.start fires here |
| `/products` | Product list — fetch `/api/products.json` → http spans |
| `/products/:id` | Detail — screen.name heuristic (`products/:id` → `ProductDetail`) |
| `/cart` | Cart — add/remove clicks target |
| `/checkout` | Checkout — `trackEvent('checkout_step_1..3')` for interactions |
| `/error-demo` | Wrapped in PulseErrorBoundary; button for manual `non_fatal` |

### Dev loop (documented in README)
```bash
cd pulse-web-otel
yarn install
yarn build                        # builds SDK dist/
yarn workspace ecommerce-demo dev # Vite on port 3002
```

---

## Phase 1 (M1) — Foundation

**Target:** `session.start` appears in ClickHouse. CORS verified. Pipeline proven end-to-end.

### Files to implement

| File | Spec doc |
|---|---|
| `src/session.ts` | `v1/01-foundation/identity.md` |
| `src/resource.ts` | `v1/01-foundation/resource.md` |
| `src/exporters.ts` | `v1/01-foundation/pipeline.md` |
| `src/persistence/indexed-db.ts` | `v1/01-foundation/pipeline.md` |
| `src/processors/global-attrs-processor.ts` | `v1/01-foundation/resource.md` |
| `src/sdk.ts` | `v1/01-foundation/sdk-lifecycle.md` |
| `src/instrumentation-registry.ts` | `v1/01-foundation/sdk-lifecycle.md` |
| `src/instrumentations/session.ts` | `v1/01-foundation/session.md` |
| `src/remote-config.ts` | `v1/01-foundation/sdk-config.md` |
| `src/__tests__/m1.test.ts` | Unit tests |

### Key implementations

**`src/session.ts` — identity (3-tier storage)**
- `getOrCreateInstallationId()`: `crypto.randomUUID()`, write order: `localStorage → sessionStorage → memory`
- `SessionProvider`: 30-min inactivity timer, `session.id` rotation, `pagehide` + BFCache `pageshow` listeners

**`src/resource.ts` — OTEL Resource (18 static attrs)**
```
browser.name, browser.version, os.name, os.version, device.type,
screen.width, screen.height, platform='web', rum.sdk.version,
project.id, service.name, service.version, url.full, ...
```

**`src/exporters.ts` — OTLP pipeline**
- `OTLPTraceExporter`, `OTLPLogExporter`, `OTLPMetricExporter` with `endpointBaseUrl` + `x-api-key`
- `BatchSpanProcessor`: `scheduledDelayMillis=5000`, `maxQueueSize=2048`, `maxExportBatchSize=512`
- `gzipBody()` via `CompressionStream` with feature-detect fallback
- `sendBeacon` flush on `pagehide`

**`src/persistence/indexed-db.ts`**
- Store: `BufferedSignal { signalType, payload, timestamp, retryCount }`
- `PersistenceExporterDecorator` wrapping OTLP exporter; drain on SDK init

**`src/sdk.ts` — 10-step init sequence**
1. validate config → 2. identity → 3. resource → 4. load cached sdk-config → 5. feature gate + processors → 6. init providers → 7. registry → 8. installAll → 9. background config fetch → 10. emit heartbeat span

**`src/instrumentations/session.ts`**
- On install: emit `pulse.type: session.start` log
- On `session.rotate`: emit `session.end` + new `session.start`
- On `pagehide`: emit `session.end` with `session.duration_ms`, `screens_visited`

### M1 exit criteria (from MILESTONES.md)
- [ ] `PulseWeb.start()` runs without errors in Chrome, Firefox, Safari
- [ ] `session.start` log in ClickHouse: `platform = 'web'`, correct `project.id`, `session.id`, `rum.sdk.version`
- [ ] `installation.id` survives page reload (localStorage)
- [ ] CORS verified on `/v1/traces`, `/v1/logs`, `/v1/metrics`
- [ ] Double `start()` is a no-op (no duplicate exporters)
- [ ] Unit tests green: identity, resource, config validation, sdk singleton

---

## Phase 2 (M2) — Interactions + SDK Config + React + First Publish

**Target:** `@dreamhorizon/pulse-web@0.1.0-alpha.1` on npm. Interaction spans in Interactions dashboard.

### Files to implement

| File | Spec doc |
|---|---|
| `src/interactions/config-fetcher.ts` | `v1/03-interactions/config.md` |
| `src/interactions/interaction-matcher.ts` | `v1/03-interactions/matching.md` |
| `src/interactions/interaction-manager.ts` | `v1/03-interactions/matching.md` |
| `src/interactions/interaction-span.ts` | `v1/03-interactions/span.md` |
| `src/processors/sampling-processor.ts` | `v1/01-foundation/sdk-config.md` |
| `src/feature-gate.ts` | `v1/01-foundation/sdk-config.md` |
| `src/processors/signal-filter-processor.ts` | `v1/01-foundation/sdk-config.md` |
| `src/integrations/react/PulseProvider.tsx` | `v1/04-frameworks/react.md` |
| `src/integrations/react/PulseErrorBoundary.tsx` | `v1/04-frameworks/react.md` |
| `src/integrations/react/useRouterTracking.ts` | `v1/04-frameworks/react.md` |
| `src/__tests__/m2.test.ts` | Unit tests |

### Key implementations

**Interactions state machine** (IDLE → ONGOING → COMPLETED/ERROR)
- 6 match operators: `eq / contains / regex / prefix / suffix / exists`
- Step sequence with configurable timeout → IDLE
- N concurrent trackers via `InteractionManager`

**APDEX scoring** (in `interaction-span.ts`)
- Satisfied: `duration < T`
- Tolerating: `T ≤ duration < 4T`
- Frustrated: `duration ≥ 4T`

**`PulseProvider`** (React)
- `typeof window !== 'undefined'` SSR guard
- `useEffect` singleton init (React StrictMode safe)
- Config from props

**`useRouterTracking`** hook
- `useLocation()` diff → emit `screen_session` span on pathname change
- `previous_screen.name` → `screen.name` transition attrs

**Demo app wire-up** (add in Phase 2)
- Wrap `App.tsx` in real `<PulseProvider config={...}>` from `.env`
- Add `useRouterTracking()` in `App.tsx`
- Add `trackEvent('checkout_step_1/2/3')` in `Checkout.tsx`
- Point `interaction-config.json` at checkout step sequence
- Add `<PulseErrorBoundary>` around `ErrorDemo` route

### M2 exit criteria
- [ ] Interaction span with `user_category` and APDEX visible in Interactions dashboard
- [ ] Config fetch failure → no crash, interactions disabled gracefully
- [ ] `sessionSampleRate: 0` → zero signals exported
- [ ] React app tracks route changes without manual wiring
- [ ] SSR guard: no `localStorage is not defined` in server render
- [ ] `npm install @dreamhorizon/pulse-web@0.1.0-alpha.1` works

---

## Phase 3 (M3) — Auto-Instrumentations

**Target:** All 6 signal types visible in Pulse dashboard under `platform = 'web'`.

### Files to implement (all parallel)

| File | Spec doc | `pulse.type` produced |
|---|---|---|
| `src/instrumentations/errors.ts` | `v1/02-instrumentations/errors.md` | `device.crash`, `non_fatal` |
| `src/instrumentations/network.ts` | `v1/02-instrumentations/network.md` | `http` span |
| `src/instrumentations/clicks.ts` | `v1/02-instrumentations/clicks.md` | `app.click` |
| `src/instrumentations/web-vitals.ts` | `v1/02-instrumentations/web-vitals.md` | `web_vital` gauge |
| `src/instrumentations/navigation.ts` | `v1/02-instrumentations/navigation.md` | `screen_load`, `screen_interactive`, `screen_session` |
| `src/__tests__/m3.test.ts` | Unit tests | — |

### Key implementations

**`errors.ts`**
- `window.onerror` → `pulse.type: device.crash` log
- `window.onunhandledrejection` → `pulse.type: non_fatal` log
- Deduplication: skip same `exception.message + stacktrace` within 1s
- Cross-origin "Script error" skip

**`network.ts`**
- Wrap `@opentelemetry/instrumentation-fetch` + `instrumentation-xml-http-request`
- Add `pulse.type: 'http'` via custom `SpanProcessor`
- Extract GraphQL `operationName` from POST body
- URL blocklist: exclude Pulse ingest endpoints

**`clicks.ts`**
- `document.addEventListener('click', ..., { capture: true })`
- Element fingerprint: `tag + id + classes + text (truncated 64 chars)`
- Rage click: sliding window 3 clicks / 700ms same element
- Normalised `x/y` viewport coordinates

**`web-vitals.ts`**
- `web-vitals` library: `onLCP / onCLS / onINP / onFCP / onTTFB`
- Each → `meter.createObservableGauge()`
- `metric.rating`: 'good' | 'needs-improvement' | 'poor'
- LCP element attribution, CLS node attribution

**`navigation.ts`**
- `screen_load` span: Navigation Timing API (`ttfb_ms`, `fcp_ms`, `load.duration_ms`)
- `screen_interactive` span: TTI via `PerformanceObserver`
- `screen_session` span: `History.pushState/replaceState/popstate` patch
- `screen.name` resolution: manual override → route patterns → path heuristic → raw pathname

**Global attrs wiring + public APIs** (`src/index.ts`)
- `PulseWeb.setScreenName(name)` → stored override used by processor
- `PulseWeb.trackEvent(name, attrs)` → custom event log
- `beforeSend` hook in pipeline

**Demo app wire-up** (add in Phase 3)
- Verify `fetch('/api/products.json')` produces `http` spans in Network tab
- Verify `ProductCard` clicks produce `app.click` logs
- Verify `ErrorDemo` throw produces `device.crash` in ClickHouse
- Verify LCP metric appears in `otel_metrics_gauge`

### M3 exit criteria
- [ ] All 6 signal types in ClickHouse under `platform = 'web'`
- [ ] Rage click detection working (`rage_click: true`)
- [ ] Web Vitals attribution populated (LCP element, CLS node)
- [ ] SPA route changes tracked automatically (React Router in demo)
- [ ] No signals emitted when `PulseDataCollectionConsent.DENIED`
- [ ] Pulse ingest endpoints excluded from network tracing
- [ ] No errors on Firefox or Safari (graceful no-ops for Chrome-only APIs)
- [ ] Unit tests green for all 6 instrumentations

---

## Phase 4 (M4) — Framework Completion + Build Pipeline

**Target:** `@dreamhorizon/pulse-web@0.1.0-alpha` published. CI enforces quality. Core < 30 KB.

### Files to implement

| File | Spec doc |
|---|---|
| `src/integrations/nextjs/PulseNextProvider.tsx` | `v1/04-frameworks/nextjs.md` |
| `src/integrations/cdn/snippet.js` | `v1/04-frameworks/cdn-vanilla.md` |
| `tsup.config.ts` (full) | `v1/05-build-distribution/` |
| `src/version.ts` | `v1/05-build-distribution/` |
| `.github/workflows/ci.yml` | `v1/05-build-distribution/` |
| `.github/workflows/publish.yml` | `v1/05-build-distribution/` |
| `.size-limit.json` | `v1/05-build-distribution/` |
| `examples/nextjs-app/` | `v1/04-frameworks/nextjs.md` |

### Key implementations

**Next.js** (App Router + Pages Router)
- `PulseNextProvider` for `app/layout.tsx` — `usePathname()` for route tracking
- `PulseNextProvider` for `_app.tsx` — `useRouter` events
- SSR guard in both variants

**CDN async snippet**
- `window.PulseWeb = window.PulseWeb || { _q: [] }` stub
- Async `<script>` inject
- On bundle load: drain `_q` queue

**`tsup.config.ts` (full)**
- Entry points: `src/index.ts`, `src/integrations/react/index.ts`, `src/integrations/nextjs/index.ts`, `src/integrations/cdn/snippet.js`
- Outputs: ESM + CJS + UMD for CDN; TypeScript declarations
- `define: { __SDK_VERSION__: process.env.npm_package_version }`
- External peer deps: `react`, `react-dom`, `react-router-dom`, `next`

**CI/CD**
- `ci.yml`: `pnpm install → typecheck → lint → test → build → size-limit` on every PR
- `publish.yml`: trigger on `pulse-web@*` tag → build → test → npm publish → S3 + CloudFront → gh release

**Bundle size enforcement** (`.size-limit.json`)
```json
[
  { "path": "dist/index.js", "limit": "30 kB" },
  { "path": "dist/react.js", "limit": "2 kB", "import": "{ PulseProvider }" },
  { "path": "dist/index.umd.js", "limit": "80 kB" }
]
```

### M4 exit criteria
- [ ] Next.js App + Pages Router: zero SSR errors
- [ ] CDN async snippet queues and drains correctly
- [ ] `npm install @dreamhorizon/pulse-web` works (ESM + CJS + types)
- [ ] CDN URL serves gzip-encoded bundle with correct `rum.sdk.version`
- [ ] Core bundle < 30 KB gzip (CI enforced)
- [ ] Release tag triggers full publish pipeline
- [ ] Example apps for React, Next.js, CDN all working

---

## Implementation Order

```
Phase 0 (scaffold + demo skeleton)     ← unblocks everything; stubs acceptable
    ↓
Phase 1 / M1 (foundation)              ← must be green before any instrumentation
    ↓
Phase 2 / M2 (interactions + React)    ← parallel-capable after M1
Phase 3 / M3 (instrumentations)        ← parallel-capable after M1
    ↓
Phase 4 / M4 (Next.js + CDN + build)   ← after M2+M3 stable
```

Phases 2 and 3 can be worked in parallel by different engineers once M1 is done.

---

## Verification Approach

### Per milestone — use the `/web-sdk` skill
```
/web-sdk verify foundation       → checks M1 exit criteria against actual files
/web-sdk verify interactions     → checks M2 exit criteria
/web-sdk verify instrumentations → checks M3 exit criteria
/web-sdk verify build            → checks M4 exit criteria
```

### Manual smoke test via demo app (all milestones)
```bash
cd pulse-web-otel
yarn build && yarn workspace ecommerce-demo dev
# Open http://localhost:3002
# Open Chrome DevTools → Network tab → filter "otlp"
# Browse to /products → /products/1 → /cart → /checkout
# Trigger "Throw Error" in /error-demo
# Click ProductCard 3x fast (rage click)
```

### ClickHouse verification queries (from MILESTONES.md)
```sql
-- M1: session pipeline
SELECT platform, project_id, session_id, rum_sdk_version
FROM otel.otel_logs WHERE pulse_type = 'session.start' AND platform = 'web' LIMIT 5;

-- M3: all signal types
SELECT DISTINCT pulse_type, platform FROM otel.otel_logs WHERE platform = 'web';
SELECT DISTINCT pulse_type, platform FROM otel.otel_traces WHERE platform = 'web';
SELECT metric_name, platform FROM otel.otel_metrics_gauge WHERE platform = 'web';
```

### Progress tracking
- Update `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` checkboxes as each exit criterion passes
- `- [ ]` → `- [x]` when Claude verifies a criterion against actual code
