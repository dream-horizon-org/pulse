# TDD: E2E tests & ecommerce demo (test-first)

## Package

pulse-web-otel

## Context

End-to-end tests using Playwright to verify all screen navigation signals are emitted correctly in a real browser environment. Update ecommerce demo to showcase signal flow with routing.

Write E2E test skeletons covering all paths (positive, gate-off, consent-off), then implement.

**TDD flow:** Red → Green → Refactor

Reference: PLAN-B-screen-navigation-spans.md; TDD-MANDATE.md § Phase 2.

## Acceptance Criteria

### Tests (write before implementation)

- [ ] **Positive path:** page load → `screen_load` + `screen_interactive` emitted with correct timing
- [ ] **Positive path:** `pulse.type` exact match on all spans
- [ ] **Positive path:** numeric values (page.load_time, tti, dns.time) are finite + non-negative
- [ ] **Positive path:** `screen.name` truthy + matches route
- [ ] **Positive path:** `session.id` truthy + consistent across spans
- [ ] **Positive path:** SPA nav → `screen_session` (old) + `screen_load` (new, spa variant) emitted
- [ ] **Positive path:** page close → visibilitychange hidden flushes pending `screen_session`
- [ ] **Boundary:** rapid SPA navigations (100ms apart) emit all screen_session spans
- [ ] **Boundary:** bfcache restore emits correct spans
- [ ] **Gate-off path:** seed SDK config `screenNavigation: false` → zero screen signals exported
- [ ] **Consent-off path:** revoke consent in SDK → zero screen signals exported
- [ ] **Demo:** ecommerce app with React Router routing; clicking links triggers signal emission
- [ ] **Demo:** signals visible in browser console or captured in interceptor

### Implementation

- [ ] E2E test file: `src/__tests__/e2e/screen-navigation.spec.ts` (Playwright)
- [ ] Test utilities: helpers to intercept OTLP signals, verify attributes
- [ ] Positive path tests: page load, SPA nav, page close
- [ ] Gate-off tests: feature disabled, consent off
- [ ] Edge case tests: rapid nav, bfcache
- [ ] Demo: `examples/ecommerce-demo/` updated with routing + console logs of signals
- [ ] All tests passing
- [ ] Coverage verified (E2E tests + unit tests combined ≥ 80%)

### Review checklist

- [ ] All assertions follow PLAN-B § Assertion floor (pulse.type, numeric, screen.name, session.id, enums)
- [ ] E2E tests deterministic (no flaky timing issues)
- [ ] Demo shows clear navigation flow (at least 3 routes)
- [ ] Signals captured correctly in interceptor (not just console logs)
- [ ] No console errors or unhandled promise rejections

## Implementation hints

1. Write E2E test skeletons capturing all paths (RED phase).
2. Use Playwright's route interception to capture OTLP exports.
3. For each signal, assert: `pulse.type`, timing, `screen.name`, `session.id`, enum validity.
4. Demo: add console.log when signals emitted, or use DevTools Network tab to show OTLP batch.
5. Use `waitForLog()` utility to wait for specific signals before asserting.

## Eval

```bash
cd pulse-web-otel && \
  yarn install --frozen-lockfile && \
  yarn build && \
  yarn test --run 'src/__tests__/e2e/**/*.spec.ts'
```

## Out of Scope

- Backend Feature enum — issue 7
- Cross-browser testing (E2E limited to Chromium)
- UI dashboard rendering (product scope)

## Blocked by

01-tdd-navigation-core, 02-tdd-signal-emission, 03-tdd-framework-integrations, 04-tdd-feature-gate-consent
