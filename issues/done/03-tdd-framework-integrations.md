# TDD: Framework integrations — React Router, Next.js (test-first)

## Package

pulse-web-otel

## Context

Write framework-specific integration hooks that detect route changes and call `navigationInstrumentation.onRouteChange()`:
- React Router v6: `useRouterTracking()` 
- Next.js app router (v13+): `useNextAppRouterTracking()`
- Next.js pages router: `useNextPagesRouterTracking()`

Write test skeletons first, then implement.

**TDD flow:** Red → Green → Refactor

Reference: TDD-MANDATE.md § Phase 2.

## Acceptance Criteria

### Tests (write before implementation)

- [ ] **Positive path:** React Router hook detects pathname change via `useLocation()`
- [ ] **Positive path:** React Router hook calls `navigationInstrumentation.onRouteChange()` with new pathname
- [ ] **Positive path:** Next.js app router hook detects route change (app/ directory)
- [ ] **Positive path:** Next.js app router hook calls `navigationInstrumentation.onRouteChange()` with correct pathname
- [ ] **Positive path:** Next.js pages router hook detects route change (pages/ directory via `useRouter()`)
- [ ] **Positive path:** Next.js pages router hook calls `navigationInstrumentation.onRouteChange()`
- [ ] **Boundary:** pathname change from `/products` to `/checkout` detected
- [ ] **Boundary:** query string change (e.g., `/products` to `/products?filter=new`) not treated as navigation (if pathname unchanged)
- [ ] **Boundary:** hash change only not treated as navigation
- [ ] **Guard logic:** SSR safety — `typeof window === "undefined"` → no-op hook
- [ ] **Guard logic:** hook doesn't break when NavigationInstrumentation not installed
- [ ] **Integration:** hook exported from `src/index.ts`
- [ ] **Integration:** each hook includes JSDoc with usage example

### Implementation

- [ ] `src/integrations/react/useRouterTracking.ts` — React Router v6 hook
- [ ] `src/integrations/next/useNextAppRouterTracking.ts` — Next.js app router hook
- [ ] `src/integrations/next/useNextPagesRouterTracking.ts` — Next.js pages router hook
- [ ] Each hook exported from `src/index.ts`
- [ ] SSR guards in each hook
- [ ] JSDoc examples in each hook
- [ ] All tests passing with coverage ≥ 80%

### Review checklist

- [ ] Each hook calls `navigationInstrumentation.onRouteChange()` on path change (not query/hash only)
- [ ] SSR guard present and tested
- [ ] Hook doesn't require NavigationInstrumentation to be pre-installed (graceful no-op if not)
- [ ] Pathname extraction correct for each framework
- [ ] No console errors or warnings

## Implementation hints

1. Write test skeletons for each integration (mocking React Router context, Next.js router, etc.).
2. React Router: use `useLocation().pathname` dependency to detect changes.
3. Next.js app router: use `useSearchParams()` + `usePathname()` to detect changes.
4. Next.js pages router: use `useRouter().pathname` to detect changes.
5. Each hook is optional (History API fallback in issue 01 works without them).

## Eval

```bash
cd pulse-web-otel && \
  yarn install --frozen-lockfile && \
  yarn workspace @dreamhorizon/pulse-web test --run 'src/integrations/**/*.test.ts'
```

## Out of Scope

- History API fallback — issue 4 (covered by core instrumentation)
- Feature gate — issue 5
- E2E tests — issue 6

## Blocked by

01-tdd-navigation-core, 02-tdd-signal-emission
