# ADR — Screen navigation signals (screen_load, screen_interactive, screen_session)

## Decision

Adopt **OTel-aligned span-based design** for screen navigation with three signal types:
- **`screen_load` span**: initial page load + SPA route changes; carries `page.load_time`, `ttfb`, `start.type`
- **`screen_interactive` span**: time-to-interactive (when DOM is interactive)
- **`screen_session` span**: time spent on screen; emitted when user navigates away

**Span emission:** via `logger.emit()` → OTLP batch queue → `loggerProvider.forceFlush()` on lifecycle boundaries.

**Feature gate:** `PulseFeature.SCREEN_NAVIGATION` (backend controls SDK feature flag + default on).

## Rejected alternative

See [PLAN-A-metrics-histogram.md](./PLAN-A-metrics-histogram.md) — metrics lose per-event context, break Android parity, and complicate ClickHouse queries.

## Flush / lifecycle strategy

### Initial page load
1. Wait for `load` event + `PerformanceNavigationTiming` ready
2. Emit `screen_load` span (start=0, end=`loadEventEnd`)
3. Emit `screen_interactive` span (start=0, end=`domInteractive`)
4. Flush via next `visibilitychange` or `pagehide` (SDK owns final flush)

### SPA route change (e.g., React Router, Next.js app router)
1. Framework router detects navigation
2. Emit final `screen_session` span for **previous** screen (start=time on old screen, end=now)
3. Emit `screen_load` span for **new** screen (start=now, end=now; SPA has no Navigation Timing)
4. Flush via `visibilitychange` hidden or SDK's `pagehide`

### Tab close / page unload
- SDK's session instrumentation already owns `pagehide`; navigation instrumentation adds final `screen_session` to that batch

**Key:** Use **`sdk.loggerProvider?.forceFlush()`** on visibility change + SPA nav, not `Logger.emit()` alone.

## Grill (Phase 3) — summary

### Double install / singleton
- `InstrumentationRegistry` private flag `installAllCompleted` prevents duplicate listeners
- `uninstall()` removes all event listeners + clears state

### Consent & feature gate
- Not installed if `PulseFeature.SCREEN_NAVIGATION` is false (backend controls)
- Not installed if consent is revoked
- Off-path E2E: seed config with `screenNavigation.enabled: false` → zero spans exported

### SSR / non-browser
- `typeof window === "undefined"` → no-op install (Next.js server render safe)

### Android parity
- `screen.name` + `last.screen.name` stamped on **every span** via `PulseGlobalAttributesProcessor` ✅
- `SpanKind.INTERNAL` (root spans, no parent context) ✅
- Start type: `cold` / `reload` / `back_forward` (Android: `cold` / `warm` / `hot` — web-specific nav types) ✅
- Lifecycle: `screen_load` + `screen_session` + `screen_interactive` ✅

### Framework integration
- React Router: call `navigationInstrumentation.onRouteChange()` from `useRouterTracking()` hook
- Next.js: use `useRouter()` to detect app router changes
- Fallback: patch `history.pushState` / `history.replaceState` for browsers not on React Router

### Optional attributes
- Omit attribute keys when value is undefined (ClickHouse treats missing != empty string)
- Example: if no `page.title`, don't emit `page.title: ""`

### BFCache & edge cases (deferred; see PLAN-B)
- `navigation.type: 'back_forward_cache'` tracked but special handling deferred
- Hash routes (`/#/...`) tracked via `window.location.hash` fallback

## Remote config & tuning

- **No remote-config knobs** for v1 (spans always enabled if feature gate on)
- Route pattern configuration **local** only (via SDK init config)
- Future: if product wants per-screen sampling, add `screenNavigation.screenSampleRates` to backend config

## Implementation note (v1 web SDK)

Signals are emitted as **OTLP logs** via `Logger.emit()` with `pulse.type` and timing attributes (`page.load_time`, `tti`, etc.). They are **not** separate OpenTelemetry span API objects in the browser exporter path v1; duration semantics are carried as attributes on log records. Future work may map these to trace spans if product requires span timing UI parity.

## Handoff notes

This ADR + PLAN-B + touchpoints unblock:
1. **Web vitals per screen** — emit web vitals (LCP, CLS, FID) in addition to `screen_load` signals
2. **UI Screens tab** — now queryable (UI already has the query; just waiting for signals)
3. **Session-to-screen correlation** — every span on a screen automatically carries `screen.name` via global processor
