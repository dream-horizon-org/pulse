# Screen Navigation Signals (v4-screen-signals)

> **Web implementation (current):** Ships **`screen_load`** and **`screen_session`** spans only. **TTI** is on the **`screen_load`** span when Navigation Timing allows. Web **does not** emit `pulse.type = screen_interactive` as a separate span. Contract: [`../instrumentations/screen-signals/SPEC.md`](../instrumentations/screen-signals/SPEC.md). The sections below preserve the original three-signal design notes for archive context.

## Problem Statement

The web SDK currently lacks structured signals for tracking user navigation and screen lifecycle. Teams cannot:

- Correlate user interactions with specific screens or route changes
- Measure time spent on individual screens
- Distinguish page load timing from SPA navigation timing
- Align web observability with Android parity (which already emits screen lifecycle signals)

This blocks the UI Screens tab, per-screen web vitals, and session-to-screen correlation in the Pulse dashboard.

## Solution

Emit three span-based signals to track user navigation and screen lifecycle:

1. **`screen_load`** — page load (initial + SPA) with timing data
   - Initial: startTime → PerformanceNavigationTiming.loadEventEnd + full timing breakdown
   - SPA: route change → small delay, no timing attributes

2. **`screen_interactive`** — time-to-interactive milestone (initial load only)
   - Initial: startTime → PerformanceNavigationTiming.domInteractive
   - SPA: not emitted (no standard interactive milestone for SPA)

3. **`screen_session`** — time spent on screen before navigation
   - Emitted when user navigates away (route change or page close)
   - Duration: time between landing and exiting

**Web vitals** (LCP, INP, CLS) emitted separately for both initial load and SPA nav (GA4 model); implementation deferred to Phase 2.

Signals are **span-based** (OTel logs with span semantics), carry `pulse.type` attribute, and are queryable by `screen.name` materialized column in ClickHouse.

## User Stories

1. **Screen correlation** — As an analyst, I want to see which screens a session visited in what order, so I can understand user navigation flows.

2. **Time on screen** — As a performance engineer, I want to measure median time spent on each screen, so I can identify UX friction points.

3. **Load vs SPA distinction** — As a product manager, I want to distinguish page load timing from SPA navigation timing, so I can optimize both flows independently.

4. **Android parity** — As an SDK maintainer, I want web and Android to emit identical screen lifecycle signals, so teams get consistent dashboards across platforms.

5. **Framework support** — As a consumer, I want my framework (React Router, Next.js, Vue Router, Remix) to be auto-instrumented without extra code.

## Implementation Decisions

### Signal type: Spans (not metrics)

- Emit as OTel logs with structured attributes (span-like semantics)
- Matches Android equivalents (ActivityInstrumentation, ScreenAttributesSpanProcessor)
- Enables per-event context (individual events queryable, not just aggregates)
- Aligns with Sentry's transaction/span-per-nav model

### Three signals per lifecycle (not one)

- `screen_load` and `screen_interactive` have different attributes and milestones
- Android emits both separately
- No network cost (same OTLP batch)

### Initial load emits both `screen_load` and `screen_interactive`

- Initial page: both signals emitted
- SPA navigation: only `screen_load` (no TTI milestone for SPA)
- Distinction via `start.type` attribute: "cold"/"reload"/"back_forward" vs "spa"

### Screen name resolution (4-step fallback)

1. Manual override: `pulse.setScreenName('CustomName')`
2. Route pattern: `{ pattern: '/products/:id', name: 'ProductDetail' }`
3. Heuristic: strip numeric/UUID segments (`/products/123` → `/products`)
4. Raw pathname: `window.location.pathname`

### Framework support

- **Explicit integrations:** React Router v6, Next.js app router (v13+), Next.js pages router
- **Fallback:** History API patch for Vue Router, Remix, SvelteKit, custom routers

### Feature gate & consent

- Backend (`Features.java`) controls feature default (default ON); SDK respects remote config override
- Consent flow: if revoked, instrumentation not installed
- SSR safe: `typeof window === "undefined"` → no-op

## Testing Decisions

### Unit tests (Vitest)

- Initial page load: `screen_load` + `screen_interactive` with timing data
- SPA nav: `screen_session` for old screen + `screen_load` for new (SPA variant)
- Route pattern matching, heuristic stripping, manual override
- Sub-100ms nav rate limiting
- SSR safety, double install guard, uninstall cleanup
- Consent off-path: zero exports

### E2E (Playwright)

- Positive path: page load → `screen_load` + `screen_interactive` with correct timing
- SPA path: initial load → nav → `screen_session` + new `screen_load`
- Page close: visibilitychange hidden flushes pending `screen_session` span
- Gate-off: seed config `screenNavigation: false` → zero exports
- Consent off: revoke consent → zero exports

### Assertion floor

- `pulse.type` exact match
- Numeric values finite + non-negative
- `screen.name` truthy + matches expected route
- `session.id` truthy
- Enum fields valid (e.g., `start.type` ∈ cold/spa/reload/back_forward)

## Acceptance Criteria

- [ ] **Navigation instrumentation class** — `NavigationInstrumentation` created, handles initial load + SPA nav detection via History API patch
- [ ] **`screen_load` initial** — emitted on page load with `start.type="cold"/"reload"/"back_forward"` and full timing attributes (page.load_time, ttfb, dns.time, tcp.time, dom.processing_time)
- [ ] **`screen_interactive` initial** — emitted on page load with `tti` attribute, starts at 0, ends at domInteractive
- [ ] **`screen_load` SPA** — emitted on route change with `start.type="spa"`, no timing attributes
- [ ] **`screen_session`** — emitted when navigating away, carries `session.duration` (time on screen)
- [ ] **Screen name resolution** — 4-step fallback wired (manual → pattern → heuristic → pathname)
- [ ] **React Router integration** — `useRouterTracking()` hook calls `navigationInstrumentation.onRouteChange()` on route change
- [ ] **Next.js app router integration** — `useNextAppRouterTracking()` hook wired; detects route change in app/ directory
- [ ] **Next.js pages router integration** — `useNextPagesRouterTracking()` hook wired; detects route change in pages/ directory
- [ ] **History API fallback** — patches `history.pushState()` and `history.replaceState()` for frameworks without explicit integration
- [ ] **Feature gate** — `PulseFeature.SCREEN_NAVIGATION` enum added to backend; feature default ON in remote config
- [ ] **Consent gate** — zero exports when consent revoked; instrumentation not installed if consent false at init
- [ ] **Global attributes** — `screen.name` and `last.screen.name` stamped on all spans via `PulseGlobalAttributesProcessor`
- [ ] **Unit tests** — all test cases from FINAL-PLAN covered (initial load, SPA nav, patterns, heuristics, rate limiting, SSR, consent off)
- [ ] **E2E tests** — positive, SPA, gate-off, consent-off paths verified with correct timing + attribute assertions
- [ ] **Ecommerce demo updated** — demo with routing showcases all three signal types
- [ ] **ClickHouse parity** — signals queryable by `screen.name`, `pulse.type`, `session.id`; no schema changes needed (existing materialized column)
- [ ] **Android parity** — web and Android emit identical `screen_load`, `screen_interactive`, `screen_session` attributes (except web-specific timing)

## Out of Scope

- **Web vitals per screen** (LCP, INP, CLS) — deferred to Phase 2; foundation laid
- **BFCache optimization** — tracked but no special handling in v1
- **Hash-based routes** — fallback to `/` + hash detection; opt-in config deferred
- **Remote sampling** — per-screen sample rates deferred
- **Soft Navigation API** — experimental standard; upgrade when stable
- **UI visualization** — backend signals ship; UI rendering deferred to product
- **Click heatmap** — separate instrumentation, deferred per interaction matrix

## Global Eval

```bash
# Web SDK: build, test, and typecheck
cd pulse-web-otel \
  && yarn install --frozen-lockfile \
  && yarn build \
  && yarn test --run

# Backend: verify SCREEN_NAVIGATION feature enum added, then test
cd ../backend/server \
  && grep -q "SCREEN_NAVIGATION" src/main/java/org/dreamhorizon/pulseserver/service/configs/models/Features.java \
  && mvn -q verify
```

## Further Notes

- **Design locked:** 2026-05-09. No further design changes without explicit decision.
- **Reference docs:** See `pulse-web-otel/web-sdk-plan/` for detailed research, ADRs, grill notes, and phase plan.
- **Key files:**
  - `FINAL-PLAN.md` — all decisions + strategy
  - `PLAN-B-screen-navigation-spans.md` — lifecycle + attributes + test matrix
  - `ADR-screen-navigation.md` — decision rationale + grill summary
  - `03-touchpoints-matrix.md` — cross-package file map
- **Framework support:** Explicit integrations for React Router v6, Next.js (app + pages); fallback to History API patch for others.
- **Handoff:** Phases 2–5 (specification → slicing → implementation → review) follow once PRD approved.
