# FINAL PLAN — Screen Navigation Signals (v4-screen-signals)

**Status:** Design locked. Ready for implementation.  
**Owner:** Jatin Khemchandani  
**Created:** 2026-05-09  
**Grill completed:** 2026-05-09  

---

## Known Terminology

**Important:** Pulse uses "session" for two distinct concepts:

| Term | Meaning | Owned by | Example |
|---|---|---|---|
| **Browser session** (`session.start` / `session.end`) | Lifetime of a browser tab/window | `SessionInstrumentation` (M1) | User opens tab → browser session starts. User closes tab → session ends. |
| **Screen session** (`screen_session` span) | Time spent on a single screen/route | `NavigationInstrumentation` (v4) | User lands on `/products` → time passes → user navigates to `/checkout` → `screen_session` emitted for `/products`. |

**Key distinction:** A single browser session contains **multiple screen sessions** (one per route change).

---

## Executive Summary

Ship three **span-based signals** to track user navigation and screen lifecycle:
- **`screen_load`** — page load (initial + SPA) with timing data
- **`screen_interactive`** — time-to-interactive milestone (initial load only)
- **`screen_session`** — time spent on screen before navigation

**Emits web vitals separately** (LCP, INP, CLS) for both initial load and SPA nav (GA4 model).

**Unblocks:** UI Screens tab + web vitals per screen + session-to-screen correlation.

---

## Design Decisions (Locked)

### 1. **Signal type: Spans (not metrics)**

✅ **Decision:** Emit as OTel logs with structured attributes (span-like semantics).

**Rationale:**
- Matches Android equivalents (`ActivityInstrumentation`, `ScreenAttributesSpanProcessor`)
- OTel trace paradigm — correct for events with duration + ordering
- ClickHouse queries on `ScreenName` materialized column (already indexed)
- Context preservation (individual events queryable, not just aggregates)

**Rejected:** Metrics (PLAN-A) — lose per-event context, break Android parity, complicate ClickHouse.

**Industry comparison:**
- PostHog: `$pageview` event (simple, no timing detail)
- Sentry: transaction/span per nav (timing + context) ← **matches Pulse**
- GA4: `page_view` event + separate `web_vitals` ← **web vitals part matches Pulse**

---

### 2. **Three signals per page lifecycle (not one)**

✅ **Decision:** Emit `screen_load`, `screen_interactive`, `screen_session` as **separate spans**.

**Rationale:**

| Signal | Why separate? |
|--------|---|
| `screen_load` | Captures full page/nav lifecycle; carries timing data (ttfb, dns, tcp, dom) |
| `screen_interactive` | Different milestone (DOM interactive vs full load); different attributes (tti only) |
| `screen_session` | Pulse-specific metric (time on screen); emitted on nav away, not on entry |

**Why not merge `screen_load` + `screen_interactive`?**
- Different attributes → one has full timing, other has tti only
- Android emits both separately
- Analytics queries can filter by `pulse.type` independently
- No network cost (same export batch)

---

### 3. **Web vitals: separate signal (GA4 model)**

✅ **Decision:** Emit **LCP, INP, CLS as a separate signal** for both initial load and SPA nav.

**Rationale:**
- Industry standard (GA4)
- Decouples navigation tracking from performance metrics
- Allows per-page web vitals without mixing with nav structure
- Separate feature flag if future product wants to gate differently

**Implementation:** Deferred to Phase 2 (after screen signals land); foundation laid in this phase.

---

### 4. **Initial page load: Two spans**

✅ **Decision:** Emit both `screen_load` AND `screen_interactive` on initial page load.

**Span details:**

#### `screen_load` (initial)
```
start: 0 (page start)
end: PerformanceNavigationTiming.loadEventEnd
attributes: {
  pulse.type: "screen_load",
  screen.name: resolved name,
  url.path: pathname,
  page.title: document.title,
  navigation.type: "navigate" | "reload" | "back_forward",
  start.type: "cold" | "reload" | "back_forward",
  page.load_time: loadEventEnd - startTime,
  ttfb: responseStart - requestStart,
  dns.time: domainLookupEnd - domainLookupStart,
  tcp.time: connectEnd - connectStart,
  dom.processing_time: domComplete - domInteractive,
  last.screen.name: previous screen,
  session.id: current session UUID
}
```

#### `screen_interactive` (initial)
```
start: 0 (page start)
end: PerformanceNavigationTiming.domInteractive
attributes: {
  pulse.type: "screen_interactive",
  screen.name: resolved name,
  url.path: pathname,
  tti: domInteractive - startTime,
  last.screen.name: previous screen,
  session.id: current session UUID
}
```

**Distinction:**
- `screen_load` = full page ready (all resources loaded, load event fired)
- `screen_interactive` = DOM is interactive (can run scripts; happens BEFORE all resources load)

**Why both?** Analytics can query:
- "Median page load time" (screen_load)
- "% users who saw content before all resources" (screen_interactive)
- "Gap between interactive and fully loaded"

---

### 5. **SPA navigation: `screen_load` only (no `screen_interactive`)**

✅ **Decision:** On SPA route change, emit **only `screen_load`** span (with SPA variant). Skip `screen_interactive`.

**Rationale:**
- No standard "interactive" milestone for SPA nav
- React rendering is synchronous (DOM updates instantly; no milestone)
- Browser PerformanceNavigationTiming API doesn't exist for SPA
- W3C Soft Navigation spec still experimental (not standard)
- Industry doesn't do this (Sentry, PostHog, GA4 don't separate TTI on SPA)

**Span details (SPA variant):**
```
screen_load {
  start: performance.now() (route change detected)
  end: performance.now() + small delay (render complete)
  attributes: {
    pulse.type: "screen_load",
    screen.name: new route name,
    url.path: new pathname,
    page.title: document.title,
    start.type: "spa",  [key difference]
    [NO timing attributes — all 0 or omitted]
    last.screen.name: previous screen,
    session.id: current session UUID
  }
}
```

**Grill finding:** No `screen_interactive` equivalent for SPA. Documented in GRILL-SESSION-NOTES.md.

---

### 6. **`screen_session` span: on navigation away**

✅ **Decision:** Emit when user navigates away (route changes or page closes).

**Span details:**
```
screen_session {
  start: route start time (when user landed on screen)
  end: performance.now() (when leaving screen)
  attributes: {
    pulse.type: "screen_session",
    screen.name: [the screen being exited],
    url.path: [the URL being left],
    last.screen.name: previous screen (before current),
    session.duration: time on screen (ms),
    session.id: current session UUID
  }
}
```

**Lifecycle:**
```
User lands on /home
  ├─ screen_load emitted
  ├─ screen_interactive emitted
  ├─ time passes (user on /home)
  └─ User navigates to /products
     ├─ screen_session emitted (for /home, duration = time elapsed)
     ├─ screen_load emitted (for /products, SPA variant)
     └─ time passes (user on /products)
        └─ User navigates away
           └─ screen_session emitted (for /products)
```

---

## Lifecycle & Flush Strategy

### Initial page load
```
Page starts loading
  ├─ Wait for load event
  ├─ PerformanceNavigationTiming ready
  ├─ Emit screen_load span
  ├─ Emit screen_interactive span
  ├─ Emit web_vitals signal (deferred to Phase 2)
  └─ Batch in OTLP queue

Page continues
  ├─ User interacts
  ├─ Next visibility change or pagehide
  └─ sdk.loggerProvider?.forceFlush() [SDK owns flush]
```

### SPA navigation
```
Route change detected (via framework or History API patch)
  ├─ Emit screen_session for previous screen
  ├─ Emit screen_load for new screen (SPA variant)
  ├─ Emit web_vitals signal (deferred to Phase 2)
  └─ Batch in OTLP queue

Batched signals
  └─ Flush on next visibilitychange hidden OR pagehide
```

### Flush boundaries
- **`visibilitychange` to hidden** — user left tab; flush pending signals
- **`pagehide`** — SDK owns this; navigation instrumentation adds to same batch
- **No explicit flush needed on SPA nav** — queued signals sent with next flush trigger

**Key:** Use **`sdk.loggerProvider?.forceFlush()`** (OTel SDK), not `Logger.emit()` alone (Logger has no flush API).

---

## Screen name resolution (4-step fallback)

**Priority order (first match wins):**

1. **Manual override** — `pulse.setScreenName('CustomName')`
2. **Route pattern** — config: `{ pattern: '/products/:id', name: 'ProductDetail' }`
3. **Heuristic** — strip numeric/UUID segments: `/products/123` → `/products`
4. **Raw pathname** — fallback: `window.location.pathname`

**Example:**
```
/products/550e8400-e29b-41d4-a716-446655440000
  ↓ heuristic (strip UUID)
/products
  ↓ or if pattern exists
ProductDetail (if matched pattern)
```

**Global stamping:** `screen.name` + `last.screen.name` stamped on **every span/log** via `PulseGlobalAttributesProcessor` (Android parity).

---

## Cross-package scope

### Web SDK (`pulse-web-otel/`)
- ✅ `src/semconv.ts` — `SCREEN_LOAD`, `SCREEN_INTERACTIVE`, `SCREEN_SESSION` constants
- ✅ `src/instrumentations/navigation.ts` — **new** NavigationInstrumentation class
- ✅ `src/types/config.ts` — route pattern config
- ✅ `src/instrumentation-registry.ts` — register + install
- ✅ `src/sdk.ts` — wire LoggerProvider to context
- ✅ `src/remote-config.ts` — `PulseFeature.SCREEN_NAVIGATION`
- ✅ `src/feature-gate.ts` — gate on feature flag + consent
- ✅ `src/integrations/react/useRouterTracking.ts` — call `navigationInstrumentation.onRouteChange()` on React Router v6 route change
- ✅ `src/integrations/next/useNextAppRouterTracking.ts` — call `navigationInstrumentation.onRouteChange()` on Next.js 13+ app router change
- ✅ `src/integrations/next/useNextPagesRouterTracking.ts` — call `navigationInstrumentation.onRouteChange()` on Next.js pages router change
- ✅ `src/integrations/next/instrumentation.ts` — verify Next.js instrumentation hook compatibility
- ✅ Tests: unit + E2E (m4-screen-signals.spec.ts)
- ✅ Demo: ecommerce-demo with routing

### Backend (`backend/server/`)
- ✅ `Features.java` — add `SCREEN_NAVIGATION` enum
- ✅ `DefaultSdkConfigTemplate.java` — add to expected features + bump count
- ✅ `DefaultSdkConfigTemplateTest.java` — update test expectations

### Backend ingestion (`backend/ingestion/`)
- ✅ No schema changes — signals stored in `otel_traces`
- ✅ `ScreenName` materialized column + bloom index already exist

### UI (`pulse-ui/`)
- ✅ No implementation needed — Screens tab already queries for `screen_load`/`screen_session`
- ✅ UI will light up when signals arrive

---

## Testing strategy

### Unit tests (Vitest)
- Initial page load: `screen_load` + `screen_interactive` with timing data
- SPA nav: `screen_session` for old + `screen_load` for new (SPA variant)
- Route pattern matching: `/products/:id` → ProductDetail
- Heuristic stripping: `/orders/UUID` → `/orders`
- Manual override: `setScreenName()` clears on next nav
- Sub-100ms nav ignored (rate limiting)
- SSR safe: `typeof window === "undefined"` no-op
- Double install guard: `installAllCompleted` flag
- Uninstall: listeners removed, state cleared
- Consent off-path: zero exports

### E2E (Playwright)
- **Positive path:** page load → screen_load + screen_interactive with correct timing
- **SPA path:** initial load → nav → screen_session + new screen_load
- **Gate-off:** seed config `screenNavigation: false` → zero exports
- **Consent off:** revoke consent → zero exports

### Assertion floor (per positive test)
- ✅ `pulse.type` exact match
- ✅ Numeric value finite + non-negative
- ✅ `screen.name` truthy + matches expected route
- ✅ `session.id` truthy
- ✅ Enum field valid (e.g., `start.type` ∈ cold/spa/reload/back_forward)

---

## Feature gate & consent

### Backend control
- `PulseFeature.SCREEN_NAVIGATION` enum controls rollout
- Default: **ON** (feature enabled by default in SDK config)
- Clients can disable via remote config

### Consent flow
- If consent revoked, instrumentation **not installed** (zero listeners)
- If installed then consent revoked, **no exports** (Logger queues but no flush)
- E2E: seed config `consentGiven: false` → zero screen signals

### SSR safety
- `typeof window === "undefined"` → no-op install
- Safe for Next.js server render, Remix, etc.

---

## Android parity checklist

| Aspect | Android | Web (Pulse) | Status |
|--------|---------|---|---|
| **Signal types** | screen_load, screen_interactive, screen_session | Same | ✅ Parity |
| **screen.name** | Activity class / annotation | Route pattern / heuristic / pathname | ✅ Parity |
| **last.screen.name** | Tracked by processor | Tracked in navigation instrumentation | ✅ Parity |
| **Global attribute propagation** | ScreenAttributesSpanProcessor | PulseGlobalAttributesProcessor | ✅ Already wired |
| **Span type** | SpanKind.INTERNAL (root) | SpanKind.INTERNAL (root) | ✅ Parity |
| **Start type** | cold / warm / hot | cold / reload / back_forward / spa | ✅ Web-specific variants |
| **Timing fields** | N/A on mobile | page.load_time, ttfb, tti, etc. | ✅ Web bonus |

---

## Framework support

### Explicit integrations (recommended)
- ✅ **React Router v6** — `useRouterTracking()` hook
- ✅ **Next.js app router** (v13+) — `useNextAppRouterTracking()` hook
- ✅ **Next.js pages router** — `useNextPagesRouterTracking()` hook

### Fallback: History API patch
If using a framework without explicit integration, the **NavigationInstrumentation** patches `history.pushState()` and `history.replaceState()` to detect route changes.

**Supported frameworks via fallback:**
- Vue Router
- Remix
- Svelte Kit
- Custom routers (any SPA using History API)

**How it works:**
```typescript
// NavigationInstrumentation patches History API
history.pushState = (...args) => {
  originalPushState(...args);
  navigationInstrumentation.onRouteChange(window.location.pathname);
};
```

**Explicit integration recommended for:**
- Best timing (called before render, not after)
- Access to route metadata (can pass route object)
- Framework-specific optimizations

---

## Deferred to future phases

### Phase 2 (after this lands)
- **Web vitals per screen** — add LCP/INP/CLS to `screen_load` attributes + UI display

### Phase 3+ (Backend/UI visualization)
- **Distinguish initial page load vs SPA nav in UI** — Both emit `screen_load` (pulse.type), but `start.type` attribute distinguishes:
  - `start.type: "cold"/"reload"/"back_forward"` = initial page load (has Navigation Timing data)
  - `start.type: "spa"` = SPA route change (no Navigation Timing data, duration near-zero)
  - Backend should surface this distinction in dashboards (e.g., separate tabs "Page Loads" vs "SPA Navigations")
  - UI Screens tab should visualize differently: page loads with timing charts, SPA navs as instant transitions

### Phase 3+ (other)
- **BFCache special handling** — `navigation.type === "back_forward_cache"` detected but no special emit
- **Hash-based routes** — fallback to `window.location.hash` when pathname === "/" (opt-in config)
- **Remote sampling** — per-screen sample rates (if product requests)
- **Soft Navigation API** — future standard (currently experimental); upgrade when stable

---

## Known limitations & tradeoffs

| Limitation | Impact | Workaround |
|---|---|---|
| **SPA nav has no TTI milestone** | No `screen_interactive` on SPA | Expected (industry standard); TTI concept doesn't apply to sync rendering |
| **Hash routes not auto-detected** | `/` + hash not recognized | Config option: `hashRoutesEnabled: true` |
| **BFCache not optimized** | Back-forward cache may be slow | Tracked but no special optimization in v1 |
| **Manual route patterns required** | Operator config burden | Provides control + predictable cardinality vs auto-detect + noise |

---

## Handoff notes

This doc supersedes all others. Keep updated as implementation proceeds:
- Implementation questions → check PLAN-B (lifecycle, attributes, test matrix)
- Design decisions → check this FINAL-PLAN (sections 1–6)
- Research context → check 01/02-research docs if context needed
- Grill findings → check GRILL-SESSION-NOTES.md (documented separately)

---

## Implementation checklist

### Phase 1 (this): Plan ✅
- ✅ Research complete
- ✅ ADR locked
- ✅ PLAN-B detailed
- ✅ Grill completed
- ✅ FINAL-PLAN written

### Phase 2: Specification
- ⏳ `/to-prd-ralph` — formalize as PRD with cross-package scope

### Phase 3: Slicing
- ⏳ `/to-issues-ralph` — break into vertical-slice issues with evals

### Phase 4: Implementation
- ⏳ `./ralph/loop.sh` — implement + test + verify

### Phase 5: Review
- ⏳ `/review` on branch before merge

---

## Reference docs (reading order)

| Doc | Purpose | When to read |
|---|---|---|
| **DESIGN.md** | What + why (1-pager) | Onboarding / quick ref |
| **FINAL-PLAN.md** (this) | All decisions + strategy | Before implementation |
| **PLAN-B-screen-navigation-spans.md** | Lifecycle + attributes + tests | Implementing spans |
| **ADR-screen-navigation.md** | Decision rationale + grill summary | Design review |
| **03-touchpoints-matrix.md** | Files touched (cross-package) | Project planning |
| **01-research-ecosystem.md** | Industry patterns + OTel alignment | Context / alternatives |
| **02-research-otel-pulse.md** | SDK integration points | Wiring phase |
| **GRILL-SESSION-NOTES.md** | Grill Q&A + findings (separate doc) | Revisit if questions arise |

---

## Sign-off

**Decision owner:** Jatin Khemchandani ✅ (grill completed)  
**Design locked:** 2026-05-09  
**Ready for:** PRD → issues → ralph/loop.sh

---

**No further design changes without explicit decision. This is the HOLY GRAIL.**
