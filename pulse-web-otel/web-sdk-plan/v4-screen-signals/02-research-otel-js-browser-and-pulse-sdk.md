# Phase 0b — Screen signals: OTel JS + Pulse SDK + Android parity

## OTel JS / Browser semconv status

**OTel spec coverage:**
- **No stable semconv for "screen" or "navigation" signals** (OTel focuses on APM: traces, metrics, logs as transport)
- `url.path`, `http.method`, `http.status_code` are stable semconv for network
- `faas.*` covers serverless; `rpc.*` covers calls — but no "user journey" or "screen lifecycle" semconv
- **Implication:** `screen_load`, `screen_interactive`, `screen_session` are **Pulse/RUM custom attributes**, not OTel spec — document clearly in ADR

**OTel JS Browser API we use:**
- `trace.getTracer()` → `startSpan(...)` / `startActiveSpan(...)` — **but NOT used here** (we emit via Logger, not Tracer API)
- `logs.getLogger()` → **`logger.emit(...)`** — this is what we use
- `LoggerProvider` → **`sdk.loggerProvider?.forceFlush()`** — SDK owns flush, Logger has none

**Key distinction:**
- `Logger` = OTel API (emit only; no lifecycle)
- `LoggerProvider` = OTel SDK (batch queue + flush + export)

We will use the **Logger API** to emit, then call **`loggerProvider.forceFlush()`** at lifecycle boundaries (visibility change, pagehide, SPA nav).

---

## Pulse SDK context

**Current state (from `PulseGlobalAttributesProcessor`):**
- `screen.name` + `last.screen.name` already stamped on **every span/log** via global processor
- `useRouterTracking()` hook updates `screen.name` on route change but **does NOT emit screen_load/session spans**
- `session.start` / `session.end` spans exist (session lifecycle) but separate from screen lifecycle

**What we add:**
- `NavigationInstrumentation` class — emits `screen_load`, `screen_interactive`, `screen_session` spans
- Hooks into `useRouterTracking()` or patches History API directly to detect SPA navigations
- Wires `loggerProvider` to flush on visibility + pagehide

**Lifecycle interaction:**
```
Session start (now)  ←──────┐
  ├─ screen_load span        │
  ├─ user interactions       │ All stamped with
  ├─ screen_session span     │ screen.name + last.screen.name
  ├─ another screen_load     │ via GlobalAttributesProcessor
  └─ screen_session span     │
Session end (now)  ←─────────┘
```

---

## Android parity checklist

| Aspect | Android | Web (proposed) | Status |
|--------|---------|---|---|
| **Signal types** | `screen_load`, `screen_interactive`, `screen_session` | Same | ✅ Parity |
| **screen.name** | Activity class name / annotation | Route pattern / heuristic / pathname | ✅ Parity (web-specific fallback chain) |
| **last.screen.name** | Tracked by processor | Tracked in navigation instrumentation | ✅ Parity |
| **Global attribute propagation** | `ScreenAttributesSpanProcessor` | `PulseGlobalAttributesProcessor` | ✅ Already wired |
| **Span type** | `SpanKind.INTERNAL` (root) | `SpanKind.INTERNAL` (root) | ✅ Parity |
| **Flush on exit** | Implicit in Activity lifecycle | `pagehide` → `forceFlush()` | ✅ Parity (SDK owns lifecycle) |
| **Start type** | `cold` / `warm` / `hot` | `cold` / `reload` / `back_forward` | ✅ Parity (web-specific nav types) |
| **Timing fields** | N/A on Android | `page.load_time`, `ttfb`, `tti`, etc. | ✅ Web bonus (browser API only) |
| **SPA route change** | Fragment navigation | History API / framework router | ✅ Parity (web-specific) |

---

## Integration points in pulse-web-otel

| Component | Interaction | Change needed? |
|-----------|-------------|---|
| `src/sdk.ts` | Initialize `NavigationInstrumentation`, store `loggerProvider` in context | Yes — add to `SdkContext` |
| `src/instrumentation-registry.ts` | Register & install navigation instrumentation | Yes — add to registry + `installAll()` |
| `src/types/config.ts` | Configuration for `routePatterns` (route name mapping) | Yes — add config type |
| `src/instrumentations/session.ts` | Already emits `session.start` / `session.end` | No change; coexist (different lifecycle) |
| `src/integrations/react/useRouterTracking.ts` | Currently only sets `screen.name`; should call navigation instrumentation | Yes — wire `onRouteChange()` call |
| `src/semconv.ts` | Define `screen_load`, `screen_interactive`, `screen_session` `pulse.type` values | Yes — add constants |
| `src/remote-config.ts` | Add `PulseFeature.SCREEN_NAVIGATION` gate | Yes — backend controls rollout |
| `src/feature-gate.ts` | Consent + feature gate checks | Yes — gate installation on feature flag |
| `src/exporters.ts` | Logger + LoggerProvider wiring | No change (already present) |
| Tests | Unit + E2E for navigation spans | Yes — new test suite |
| Demo | Add UI element that triggers route changes | Yes — ecommerce demo needs routing |

---

## Key API boundaries & gotchas

1. **Logger vs LoggerProvider confusion:**
   - ❌ Don't call `logger.flush()` (doesn't exist)
   - ✅ Call `sdk.loggerProvider?.forceFlush()` (SDK owns the pipeline)

2. **Span vs Log decision we made (logs):**
   - Pulse emits span-like data **as OTel logs with structured attrs** (not trace SDK spans)
   - This is fine; Pulse export → ClickHouse treats both as signals

3. **SSR / non-browser:**
   - `typeof window === "undefined"` → no-op in `install()`

4. **Double install guard:**
   - Use `InstrumentationRegistry` private `installAllCompleted` flag

5. **Browser Navigation Timing API availability:**
   - `PerformanceNavigationTiming` exists only after `load` event
   - SPA navigations have no `PerformanceNavigationTiming` (use `performance.now()` only)
