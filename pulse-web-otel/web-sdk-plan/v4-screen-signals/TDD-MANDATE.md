# TDD Mandate — Screen Navigation Signals & Future Instrumentations

**Effective:** v4-screen-signals phase onwards  
**Scope:** All pulse-web-otel instrumentation development (M1, M2, M3, etc.)  
**Owner:** Jatin Khemchandani

---

## Philosophy

Test-Driven Development is mandatory for pulse-web-otel instrumentations because:

1. **Signal correctness is non-negotiable** — incorrect signals corrupt dashboards, analytics, and user experience
2. **Concurrency is hard** — browser lifecycle events (visibility, unload, bfcache) are unpredictable; tests force edge case thinking
3. **Guard logic is subtle** — feature gates + consent + double-install interactions have invisible bugs; TDD surfaces them
4. **Regression prevention** — Pulse signals are read-only from the backend; once shipped, bugs are permanent

---

## The Flow

### Phase 1: PRD → Test Suite (before implementation)

**Given a PRD (from `/to-prd-ralph`), the FIRST task must ALWAYS be:**

**"Generate all possible test cases for this PRD"**

**Process:**

1. **Read the PRD** — understand signals, attributes, lifecycle, gates
2. **Enumerate test categories:**
   - ✅ Positive path (happy case)
   - ✅ Boundary conditions (edge cases)
   - ✅ Guard logic (consent, feature gate, double-install)
   - ✅ Concurrency (rapid events, overlapping timers)
   - ✅ Cleanup (uninstall, listener removal)
   - ✅ Integration (signal batching, flush guarantees)
   - ✅ Regression (from prior bugs or edge cases)

3. **Write test skeletons** (empty bodies, descriptive names):
   ```typescript
   test("positive path: screen_load emitted with correct attributes", async () => {
     // TODO: implement
   });
   
   test("edge case: rapid navigations emit all screen_session spans", async () => {
     // TODO: implement
   });
   
   test("guard: consent revoked after install blocks flush", async () => {
     // TODO: implement
   });
   
   // ... 20+ test skeletons
   ```

4. **Add to issue description** — each skeleton becomes a checklist item:
   ```
   ## Tests to implement
   - [ ] positive path: screen_load emitted with correct attributes
   - [ ] edge case: rapid navigations emit all screen_session spans
   - [ ] guard: consent revoked after install blocks flush
   ```

5. **Ralph evaluates against PRD** — for each test skeleton, ralph ensures coverage spans the spec

---

### Phase 2: Implementation with Red-Green-Refactor

**For each test:**

1. **Red:** Write the test, run it, watch it fail (test framework finds missing code)
2. **Green:** Implement just enough code to make the test pass
3. **Refactor:** Clean up, extract patterns, but don't gold-plate

**Example:**

```typescript
// Test (RED — fails because NavigationInstrumentation doesn't exist)
test("screen_load emitted on initial page load", async ({ page }) => {
  const load = await otlp.waitForLog("screen_load");
  expect(load["pulse.type"]).toBe("screen_load");
  expect(load["page.load_time"]).toBeGreaterThan(0);
});

// Implementation (GREEN — minimal code to pass)
export class NavigationInstrumentation {
  install(sdk: SdkContext): void {
    window.addEventListener("load", () => {
      const nav = performance.getEntriesByType("navigation")[0];
      sdk.logger.emit({
        attributes: {
          "pulse.type": "screen_load",
          "page.load_time": nav.loadEventEnd - nav.startTime,
        }
      });
    });
  }
}

// Refactoring (REFACTOR — extract timing logic, add error handling)
private emitScreenLoad(nav: PerformanceNavigationTiming): void {
  // ... extracted
}
```

---

## Issue Template

Every instrumentation issue must follow this template:

```markdown
## Task

Implement [signal type] instrumentation.

## Acceptance Criteria

### Tests (required — write before implementation)
- [ ] Positive path: [test name]
- [ ] Edge case: [test name]
- [ ] Guard logic: [test name]
- [ ] Concurrency: [test name]
- [ ] Integration: [test name]
- [ ] Regression: [test name]

### Implementation
- [ ] `src/instrumentations/[name].ts` — main class
- [ ] `src/semconv.ts` — pulse.type + attributes
- [ ] Unit tests all passing
- [ ] E2E tests all passing
- [ ] Test coverage ≥ 80% on new/changed lines

### Review checklist
- [ ] No listener leaks (uninstall cleanup verified)
- [ ] Consent gate respected (at emit + flush)
- [ ] Feature gate respected
- [ ] Double-install guard active
- [ ] Android parity checked (if applicable)
```

---

## Test Categories (for PRD readers)

When writing test skeletons, use these categories:

### 1. Positive path
**What should happen when everything is normal.**
```
test("screen_load emitted on initial page load with timing data")
test("screen_session emitted with correct duration on SPA nav")
test("all attributes stamped (session.id, screen.name, platform)")
```

### 2. Boundary conditions
**Off-by-one, empty, zero, max values, exact thresholds.**
```
test("sub-100ms navigation ignored (rate limiting)")
test("screen.name resolution on root path /")
test("max route patterns limit")
```

### 3. Guard logic
**Feature gates, consent, double-install, SSR.**
```
test("consent OFF at install: instrumentation not installed")
test("consent revoked after install: flush blocked")
test("feature gate OFF: signals not emitted")
test("double installAll(): listeners not duplicated")
test("SSR (typeof window === undefined): no-op install")
```

### 4. Concurrency & timing
**Rapid events, overlapping timers, visibility changes.**
```
test("concurrent rapid navigations: all screen_session emitted")
test("navigation during visibility change: no race condition")
test("pagehide during pending flush: flush completes")
```

### 5. Cleanup & lifecycle
**Uninstall, event listener removal, state clearing.**
```
test("uninstall(): all listeners removed")
test("uninstall() + reinstall(): re-registers listeners")
test("listener cleanup on error: no dangling listeners")
```

### 6. Integration
**Interaction with other systems: session lifecycle, global attributes, batching.**
```
test("screen.name stamped on all signals by GlobalAttributesProcessor")
test("screen_session queued with session.start/end in same batch")
test("screen signals respect consent gate like session signals")
```

### 7. Regression (if applicable)
**Known bugs, prior incidents, edge cases from code review.**
```
test("bfcache restore: screen_session + screen_load both emitted")
test("visibility change during SPA nav: no duplicate session")
```

---

## Ralph's Role (loop.sh integration)

Ralph will:

1. **Scan issue description** for test checklist (`- [ ] test name`)
2. **Validate** that test skeletons exist (file: `src/__tests__/navigation-instrumentation.test.ts`)
3. **Run** test suite before each iteration:
   - If tests fail: fix implementation, re-run
   - If tests pass but coverage < 80%: add more tests
4. **Track** coverage + test pass rate in progress.log
5. **Block merge** if coverage < 80% or any test fails

---

## Success metrics

- **All tests pass** before code review
- **Coverage ≥ 80%** on changed lines (JaCoCo for backend, Vitest for web SDK)
- **Zero regressions** on prior instrumentations
- **No listener leaks** (cleanup verified in uninstall tests)

---

## When to deviate

**Skip TDD only if ALL of these are true:**
1. Bug fix in existing code (not new feature)
2. Regression test already written for the bug
3. Fix is < 5 lines of code
4. No new signal types or attributes

Otherwise: TDD mandatory.

---

## Examples

### Good (TDD-first)
```
Issue: Implement screen_load + screen_interactive + screen_session

Tests (write first):
- [ ] positive: screen_load emitted on initial page load
- [ ] positive: screen_interactive emitted at domInteractive
- [ ] positive: screen_session emitted on navigation away
- [ ] edge: sub-100ms navigation ignored
- [ ] guard: consent OFF blocks all signals
- [ ] concurrency: rapid nav emits all sessions
- [ ] cleanup: uninstall removes listeners

Implementation (after tests):
- NavigationInstrumentation class
- Lifecycle event handlers
- Flush guarantees
```

### Bad (implementation-first)
```
Issue: Implement screen navigation

Implementation:
- (write code first, tests later)

Tests:
- (scattered, incomplete, written as afterthought)
- (miss edge cases, guard logic)
```

---

## References

- **This plan:** `/Users/jatinkhemchandani/Desktop/pulse/pulse-web-otel/web-sdk-plan/v4-screen-signals/FINAL-PLAN.md`
- **Test examples:** `/Users/jatinkhemchandani/Desktop/pulse/pulse-web-otel/web-sdk-plan/v4-screen-signals/PLAN-B-screen-navigation-spans.md` (see "Hardened test cases")
- **Integration:** `.cursor/skills/web-sdk-instrumentation-lifecycle/` → use `/web-sdk` skill to reference this
