# M3 — Auto-Instrumentations (5 signals + public API wiring)

## Context
Implements all 5 auto-instrumentations in parallel — errors, network, clicks, web vitals, navigation. Each file is independent and can be assigned to a different engineer or agent. After M3 the dashboard shows all 6 `pulse.type` values (`session.start` from M1 + the 5 new ones) under `platform='web'`. Also wires the complete public API (`setScreenName`, `trackEvent`, `beforeSend`) and the `GlobalAttrsProcessor` dynamically.

## Prerequisites
- M1 complete: SDK pipeline proven, `session.start` in ClickHouse
- M2 complete (optional but recommended): `screen.name` resolution chain from navigation is used by interactions

## Spec Docs to Read First
Read the specific file for each instrumentation you're implementing:
- Errors: `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/errors.md`
- Network: `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/network.md`
- Clicks: `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/clicks.md`
- Web Vitals: `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/web-vitals.md`
- Navigation: `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/navigation.md`
- Index (for cross-cutting done criteria): `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/index.md`

## Files to Create

| File | `pulse.type` produced | Spec |
|---|---|---|
| `src/instrumentations/errors.ts` | `device.crash`, `non_fatal` | `errors.md` |
| `src/instrumentations/network.ts` | `http` span | `network.md` |
| `src/instrumentations/clicks.ts` | `app.click` | `clicks.md` |
| `src/instrumentations/web-vitals.ts` | `web_vital` gauge | `web-vitals.md` |
| `src/instrumentations/navigation.ts` | `screen_load`, `screen_interactive`, `screen_session` | `navigation.md` |
| `src/__tests__/m3.test.ts` | Unit tests for all 5 | — |

## Files to Update
| File | Change |
|---|---|
| `src/index.ts` | Export `PulseWeb.setScreenName()`, `PulseWeb.trackEvent()`, `PulseWeb.reportException()` as real methods on the singleton |
| `src/sdk.ts` | Implement `setScreenName()`, `trackEvent()`, `reportException()`, `beforeSend` hook plumbing |
| `src/processors/global-attrs-processor.ts` | Complete dynamic attrs: `screen.name`, `url.path`, `page.url`, `network.connection.type` injected on every signal |
| `src/instrumentation-registry.ts` | Register all 5 new instrumentations in `installAll()` |

---

## Per-Instrumentation Notes

### `errors.ts`
- `window.addEventListener('error', handler)` → emit `pulse.type: 'device.crash'` log
- `window.addEventListener('unhandledrejection', handler)` → emit `pulse.type: 'non_fatal'` log
- Deduplication: `Set<string>` keyed by `exception.message + ':' + stacktrace`; clear after 1 second
- Cross-origin guard: if `event.message === 'Script error.'` and no stack → skip (cross-origin script)
- Pre-init queue: capture errors before `PulseWeb.start()` → drain queue on init
- Required attrs: `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename`, `error.lineno`, `error.colno`, `url.path`

### `network.ts`
- Base: `new FetchInstrumentation()` + `new XMLHttpRequestInstrumentation()` from `@opentelemetry/instrumentation-fetch` + `instrumentation-xml-http-request`
- Add `pulse.type: 'http'` via a custom `SpanProcessor.onStart()`
- URL blocklist: if `span.attributes['http.url']` contains `config.endpointBaseUrl` → call `span.setAttribute('pulse.blocked', true)` then drop in exporter or filter
- GraphQL: on POST request, read body JSON → if `operationName` key exists → set `graphql.operation.name` + `graphql.operation.type`
- Required attrs: `http.method`, `http.url`, `http.status_code`, `http.duration`, `net.peer.name`

### `clicks.ts`
- `document.addEventListener('click', handler, { capture: true })` — capture phase catches all clicks
- Element fingerprint: `${el.tagName.toLowerCase()}#${el.id}.${el.className}` + inner text truncated to 64 chars
- Rage click: sliding window — keep last 10 click timestamps per element fingerprint; if 3+ within 700ms → `rage_click: true`
- Coords: `event.clientX / window.innerWidth` and `event.clientY / window.innerHeight` → normalised 0–1
- Required attrs: `view.target.class_name`, `view.target.id`, `touch.coordinates.x`, `touch.coordinates.y`, `rage_click`

### `web-vitals.ts`
- Import: `import { onLCP, onCLS, onINP, onFCP, onTTFB } from 'web-vitals'`
- Each callback: `meter.createObservableGauge('web_vital').addCallback(obs => obs.observe(metric.value, attrs))`
- Attrs per metric: `metric.name` (LCP/CLS etc.), `metric.value`, `metric.rating` ('good'|'needs-improvement'|'poor')
- LCP: add `lcp.element` (element tag + id) from `metric.attribution.lcpEntry.element`
- CLS: add `cls.largest_shift_source` from `metric.attribution.largestShiftSource`
- Inject global attrs at observe time (not at meter creation time)

### `navigation.ts`
- **`screen_load` span:** Start at `performance.timing.navigationStart`; attrs: `ttfb_ms` (`responseStart - navigationStart`), `fcp_ms` (from `PerformanceObserver 'paint'`), `load.duration_ms` (`loadEventEnd - navigationStart`)
- **`screen_interactive` span:** End when `PerformanceObserver 'longtask'` queue goes quiet for 50ms (TTI approximation); attr: `tti_ms`
- **`screen_session` span:** Patch `history.pushState` + `history.replaceState` + `window.addEventListener('popstate')` — each navigation ends old span + starts new one
- **`screen.name` resolution (4-step chain):**
  1. `PulseWeb.setScreenName()` override (highest priority)
  2. Route patterns: `config.routePatterns: [{pattern: '/products/:id', name: 'ProductDetail'}]`
  3. Heuristic: strip trailing UUIDs/numbers from path segments (`/products/123` → `/products/:id`)
  4. Raw `window.location.pathname` (fallback)
- Required attrs on all navigation spans: `screen.name`, `url.path`, `platform='web'`
- Graceful no-ops: `PerformanceObserver` for `longtask` not available in Firefox/Safari → skip TTI span, no error

## Done Criteria
- [ ] All 6 signal types visible in Pulse dashboard under `platform = 'web'`
  - `session.start` / `session.end` (from M1)
  - `device.crash` / `non_fatal` (errors.ts)
  - `http` (network.ts)
  - `app.click` (clicks.ts)
  - `web_vital` gauge (web-vitals.ts)
  - `screen_load` / `screen_interactive` / `screen_session` (navigation.ts)
- [ ] Rage click detection: 3 rapid clicks on `<RageClickButton>` → `rage_click: true` in ClickHouse
- [ ] Web Vitals: LCP + CLS attribution fields populated
- [ ] SPA route changes in demo → `screen_session` spans (navigate `/products` → `/cart`)
- [ ] Error in `/error-demo` → `device.crash` log in ClickHouse
- [ ] Pulse ingest endpoint URLs excluded from `http` spans
- [ ] `PulseDataCollectionConsent.DENIED` → zero signals emitted across all 5 instrumentations
- [ ] No thrown errors in Firefox or Safari (graceful no-ops for `longtask`, `resourceTiming`)
- [ ] `beforeSend` hook: returning `null` drops the signal; returning modified object changes it
- [ ] Unit tests green: error deduplication, network blocklist, rage click threshold, vitals gauge, screen.name heuristic, consent gate

## Verification

### Unit tests
```bash
cd pulse-web-otel && yarn test --run src/__tests__/m3.test.ts
```

### E2E tests
```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn e2e --grep "@M3" --project=chromium    # Chrome
yarn e2e --grep "@M3" --project=firefox    # Firefox — verify graceful no-ops
yarn e2e --grep "@M3" --project=webkit     # Safari
# Or from SDK root: yarn workspace ecommerce-demo e2e --grep "@M3"
# Covers: device.crash, non_fatal, http, app.click, rage_click, web_vital,
#         screen_load, screen_session, screen.name heuristic, consent gate
```

### Manual + ClickHouse
```bash
yarn build && yarn workspace ecommerce-demo dev
# DevTools Network → filter v1/ for each signal type
```
```sql
SELECT DISTINCT pulse_type, platform FROM otel.otel_logs WHERE platform = 'web';
SELECT DISTINCT pulse_type, platform FROM otel.otel_traces WHERE platform = 'web';
SELECT DISTINCT metric_name FROM otel.otel_metrics_gauge WHERE platform = 'web';

SELECT rage_click, count() FROM otel.otel_logs
WHERE pulse_type = 'app.click' AND platform = 'web' GROUP BY rage_click;

-- Consent gate: load with ?pulse_consent=denied, then:
SELECT count() FROM otel.otel_logs WHERE platform = 'web'
AND Timestamp > now() - interval 1 minute;
```
Update `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M3 checkboxes when all pass.
