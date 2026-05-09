# TDD: NavigationInstrumentation core (test-first)

## Package

pulse-web-otel

## Context

Foundation for screen navigation signals. First task: write comprehensive test skeletons covering all edge cases and guard logic. Then implement NavigationInstrumentation class to pass tests.

**TDD flow:** Red (tests fail) → Green (implement) → Refactor (clean)

Reference: TDD-MANDATE.md § Phases 1–2.

## Acceptance Criteria

### Tests (write before implementation)

- [ ] **Positive path:** screen name resolution (manual override) — highest priority
- [ ] **Positive path:** screen name resolution (route pattern match)
- [ ] **Positive path:** screen name resolution (heuristic UUID/numeric stripping)
- [ ] **Positive path:** screen name resolution (raw pathname fallback)
- [ ] **Boundary:** root path `/` resolves correctly
- [ ] **Boundary:** deep path `/a/b/c/d` heuristic stripping
- [ ] **Boundary:** UUID in pathname stripped by heuristic
- [ ] **Guard logic:** SSR safety — `typeof window === "undefined"` → no-op install
- [ ] **Guard logic:** double install guard — `installAllCompleted` flag prevents duplication
- [ ] **Guard logic:** History API patches preserve original behavior (original function called)
- [ ] **Cleanup:** uninstall removes listeners + clears state
- [ ] **Cleanup:** reinstall after uninstall re-registers listeners
- [ ] **Integration:** screen.name stamped on all spans via GlobalAttributesProcessor

### Implementation

- [ ] `src/instrumentations/navigation.ts` — NavigationInstrumentation class (install, uninstall, onRouteChange, screen name resolution)
- [ ] History API patch — `history.pushState()` and `history.replaceState()` patched on install
- [ ] State tracking — currentScreen, lastScreen, currentScreenStartTime tracked
- [ ] Screen name resolution — 4-step fallback (manual → pattern → heuristic → pathname)
- [ ] `src/types/config.ts` — route pattern config interface (if not exists)
- [ ] All tests passing
- [ ] Test coverage ≥ 80% on `src/instrumentations/navigation.ts`

### Review checklist

- [ ] No listener leaks verified in uninstall test
- [ ] Screen name resolution order correct (first match wins)
- [ ] History API patch doesn't interfere with other code
- [ ] SSR guard present and tested
- [ ] State cleared on uninstall (no dangling references)

## Implementation hints

1. **Write test skeletons first** with descriptive names (no implementation, just `expect()` placeholders).
2. **Run tests** (RED) — watch them all fail.
3. **Implement** — add just enough code to make each test pass (GREEN).
4. **Refactor** — extract methods, add error handling, but don't over-design.
5. **Verify coverage** — `yarn test --run src/instrumentations/navigation.test.ts --coverage` must show ≥80%.

## Eval

```bash
cd pulse-web-otel && \
  yarn install --frozen-lockfile && \
  yarn workspace @dreamhorizon/pulse-web test --run src/instrumentations/navigation.test.ts
```

## Out of Scope

- Signal emission (screen_load, screen_interactive, screen_session) — issue 2
- Feature gate logic — issue 4
- Consent gate — issue 4

## Blocked by

None
