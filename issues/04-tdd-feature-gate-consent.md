# TDD: Feature gate & consent (test-first)

## Package

pulse-web-otel

## Context

Integrate NavigationInstrumentation with SDK's feature gate (`PulseFeature.SCREEN_NAVIGATION`) and consent flow. If either is disabled, instrumentation must not be installed and signals must not be exported.

Write test skeletons covering all guard logic, then implement.

**TDD flow:** Red → Green → Refactor

Reference: TDD-MANDATE.md § Test Categories § Guard logic; PRD § Feature gate & consent.

## Acceptance Criteria

### Tests (write before implementation)

- [ ] **Guard logic:** feature gate OFF at init → instrumentation not installed (zero listeners)
- [ ] **Guard logic:** consent OFF at init → instrumentation not installed (zero listeners)
- [ ] **Guard logic:** feature gate ON + consent ON → instrumentation installed normally
- [ ] **Guard logic:** consent revoked after install → listeners active but flush blocked (pending spans queued but not exported)
- [ ] **Guard logic:** feature gate OFF after install → signals queued but not flushed
- [ ] **Guard logic:** SSR safety — `typeof window === "undefined"` → feature gate check short-circuits, no install
- [ ] **Boundary:** remote config override `screenNavigation: false` disables feature
- [ ] **Boundary:** remote config override `screenNavigation: true` enables feature
- [ ] **Integration:** consent gate checked before listener registration
- [ ] **Integration:** feature gate checked before listener registration
- [ ] **Regression:** double install with different gate states (on then off) doesn't leak listeners

### Implementation

- [ ] `src/feature-gate.ts` — check `PulseFeature.SCREEN_NAVIGATION` before install
- [ ] `src/consent.ts` — check `consentGiven` flag before install
- [ ] NavigationInstrumentation.install() — early return if gate or consent false
- [ ] Logger flush behavior — when consent off, log queued but flush blocked
- [ ] Remote config integration — respect `screenNavigation` override
- [ ] All tests passing with coverage ≥ 80%

### Review checklist

- [ ] Feature gate checked before any listener registration
- [ ] Consent gate checked before any listener registration
- [ ] Zero side effects if either gate is false (no partial install)
- [ ] Consent revoked after install — spans queued but not exported (verified in test)
- [ ] SSR guard prevents code execution on server
- [ ] No console warnings if gates are false

## Implementation hints

1. Write test skeletons: gate OFF, consent OFF, revoked after install, SSR, config override.
2. Check gates at beginning of `install()` — early return if false (RED phase).
3. Implement just enough to make tests pass (GREEN phase).
4. Consent revoked = Logger queues spans but `forceFlush()` blocks export (don't modify Logger itself).
5. Feature gate OFF = skip installing listeners entirely.

## Eval

```bash
cd pulse-web-otel && \
  yarn install --frozen-lockfile && \
  yarn workspace @dreamhorizon/pulse-web test --run src/feature-gate.test.ts && \
  yarn workspace @dreamhorizon/pulse-web test --run src/consent.test.ts && \
  yarn workspace @dreamhorizon/pulse-web test --run 'src/instrumentations/navigation.test.ts 2>&1' | grep -E "(feature|consent|gate)"
```

## Out of Scope

- Backend Feature enum — issue 7 (just configuration)
- E2E tests — issue 6
- UI visualization of gated state

## Blocked by

01-tdd-navigation-core, 02-tdd-signal-emission
