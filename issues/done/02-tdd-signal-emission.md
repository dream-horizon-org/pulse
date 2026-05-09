# TDD: Span emission — screen_load, screen_interactive, screen_session (test-first)

## Package

pulse-web-otel

## Context

Emit three span types on page lifecycle events:
- Initial page load: `screen_load` + `screen_interactive` with timing data
- SPA navigation: `screen_session` (previous) + `screen_load` (new, SPA variant)

Write test skeletons first, then implement emission logic.

**TDD flow:** Red → Green → Refactor

Reference: PLAN-B-screen-navigation-spans.md § Attributes + Lifecycle; TDD-MANDATE.md § Test Categories.

## Acceptance Criteria

### Tests (write before implementation)

- [ ] **Positive path:** `screen_load` emitted on initial page load with `start.type="cold"` and full timing attrs (page.load_time, ttfb, dns.time, tcp.time, dom.processing_time)
- [ ] **Positive path:** `screen_interactive` emitted on initial page load with `tti` attribute
- [ ] **Positive path:** timing values are finite, non-negative, correct magnitude (ms)
- [ ] **Positive path:** all spans carry `pulse.type`, `screen.name`, `url.path`, `last.screen.name`, `session.id`
- [ ] **Positive path:** `screen_interactive` always emitted before `screen_load` (domInteractive < loadEventEnd)
- [ ] **Positive path:** SPA nav emits `screen_session` (old screen, duration) + `screen_load` (new, `start.type="spa"`, no timing)
- [ ] **Boundary:** reload loads (`start.type="reload"`) with timing
- [ ] **Boundary:** back-forward loads (`start.type="back_forward"`) with timing
- [ ] **Boundary:** fast page loads (< 100ms) still emit correctly
- [ ] **Boundary:** slow page loads (> 10s) emit with correct timing
- [ ] **Boundary:** sub-100ms SPA nav ignored (rate limit)
- [ ] **Edge case:** PerformanceNavigationTiming missing data handled gracefully (omit zero-valued attributes)
- [ ] **Integration:** signals queued in OTLP batch (no immediate flush)
- [ ] **Integration:** screen.name stamped on all signals

### Implementation

- [ ] `src/instrumentations/navigation.ts` — emit methods (emitScreenLoad, emitScreenInteractive, emitScreenSession)
- [ ] `src/semconv.ts` — constants for SCREEN_LOAD, SCREEN_INTERACTIVE, SCREEN_SESSION `pulse.type` values
- [ ] PerformanceNavigationTiming extraction — timing calculation (ttfb, dns, tcp, dom.processing_time)
- [ ] SPA nav detection — route change calls emit handlers
- [ ] Rate limiting — sub-100ms nav ignored
- [ ] State update after emit — currentScreen, lastScreen set correctly
- [ ] All tests passing with coverage ≥ 80%

### Review checklist

- [ ] Timing extraction handles missing PerformanceNavigationTiming gracefully
- [ ] Optional attributes (0-valued) are omitted (don't emit `"ttfb": 0`)
- [ ] `start.type` attribute correctly set (cold/reload/back_forward/spa)
- [ ] Event order verified (screen_interactive before screen_load on initial)
- [ ] Rate limiting (sub-100ms) prevents false positives
- [ ] No console errors or warnings in tests

## Implementation hints

1. Write test skeletons capturing all scenarios above (RED phase).
2. PerformanceNavigationTiming is only available after load event; wait for it.
3. For SPA nav, use `performance.now()` (no PerformanceNavigationTiming API).
4. Emit to SDK's LoggerProvider (don't flush immediately).
5. Extract timing as early as possible (performance.now() drift on later access).

## Eval

```bash
cd pulse-web-otel && \
  yarn install --frozen-lockfile && \
  yarn workspace @dreamhorizon/pulse-web test --run src/instrumentations/navigation.test.ts 2>&1 | grep -E "(screen_load|screen_interactive|screen_session)"
```

## Out of Scope

- Feature gate logic — issue 4
- Consent gate — issue 4
- Framework integrations — issue 3
- E2E tests — issue 5

## Blocked by

01-tdd-navigation-core
