# M2 — Interactions + SDK Config + React + First Publish

## Context
Builds on M1 to deliver the highest-value Pulse-specific feature: multi-step user journey tracking with APDEX scoring. Adds full remote SDK Config (sampling, feature gates, signal filters), React integration (`<PulseProvider>`, `<PulseErrorBoundary>`, `useRouterTracking`), and publishes the first npm alpha. Wires the ecommerce demo to use the real React integration.

## Prerequisites
- M1 complete: `session.start` appearing in ClickHouse, all M1 done criteria checked
- `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M1 checkboxes all `[x]`

## Spec Docs to Read First
1. `pulse-web-otel/web-sdk-plan/v1/03-interactions/config.md` — CDN config fetch + InteractionConfig type
2. `pulse-web-otel/web-sdk-plan/v1/03-interactions/matching.md` — state machine + 6 match operators + concurrent trackers
3. `pulse-web-otel/web-sdk-plan/v1/03-interactions/span.md` — APDEX scoring + full span attribute contract
4. `pulse-web-otel/web-sdk-plan/v1/01-foundation/sdk-config.md` — SamplingProcessor, FeatureGate, SignalFilterProcessor wire-up into `sdk.ts`
5. `pulse-web-otel/web-sdk-plan/v1/04-frameworks/react.md` — PulseProvider, PulseErrorBoundary, useRouterTracking

## Files to Create

| File | Spec doc |
|---|---|
| `src/interactions/config-fetcher.ts` | `03-interactions/config.md` |
| `src/interactions/interaction-matcher.ts` | `03-interactions/matching.md` |
| `src/interactions/interaction-manager.ts` | `03-interactions/matching.md` |
| `src/interactions/interaction-span.ts` | `03-interactions/span.md` |
| `src/processors/sampling-processor.ts` | `sdk-config.md` (already created in M1 — complete implementation here) |
| `src/feature-gate.ts` | `sdk-config.md` (already created in M1 — complete implementation here) |
| `src/processors/signal-filter-processor.ts` | `sdk-config.md` (already created in M1 — complete implementation here) |
| `src/integrations/react/index.ts` | `react.md` — barrel export |
| `src/integrations/react/PulseProvider.tsx` | `react.md` |
| `src/integrations/react/PulseErrorBoundary.tsx` | `react.md` |
| `src/integrations/react/useRouterTracking.ts` | `react.md` |
| `src/__tests__/m2.test.ts` | Unit tests |

## Files to Update
| File | Change |
|---|---|
| `src/sdk.ts` | Wire SamplingProcessor + SignalFilterProcessor into providers; wire FeatureGate into registry |
| `package.json` | Add `"./react"` exports entry pointing to `dist/react.js` |
| `tsup.config.ts` | Add `src/integrations/react/index.ts` as second entry point |
| `examples/ecommerce-demo/src/App.tsx` | Wrap with real `<PulseProvider>` + add `useRouterTracking()` |
| `examples/ecommerce-demo/src/routes/Checkout.tsx` | Add `trackEvent('checkout_step_1/2/3')` calls |
| `examples/ecommerce-demo/src/routes/ErrorDemo.tsx` | Replace stub ErrorBoundary with `<PulseErrorBoundary>` |
| `examples/ecommerce-demo/public/interaction-config.json` | Populate with checkout step interaction config |

## Key Implementation Notes

### Interactions State Machine (`src/interactions/interaction-matcher.ts`)
- States: `IDLE → ONGOING → COMPLETED | ERROR | TIMEOUT`
- Each config has `steps[]` with `eventName` + match operator + optional `value`
- 6 operators: `eq`, `contains`, `regex`, `prefix`, `suffix`, `exists`
- `timeout` in config (ms): if ONGOING and timeout elapses → back to IDLE, no span emitted
- On COMPLETED: call `InteractionSpanBuilder.create()` to emit span

### Concurrent Tracking (`src/interactions/interaction-manager.ts`)
- Fan-out: every `trackEvent()` call broadcasts to ALL active `InteractionEventsTracker` instances
- Each tracker is independent (different config, different state machine instance)
- Blacklist: skip events matching `blacklistedEventNames[]`; skip if `screen.name` matches `blacklistedScreenNames[]`

### APDEX (`src/interactions/interaction-span.ts`)
- `T` = config's `apdex_t` threshold (ms)
- Satisfied: `duration < T` → `user_category: 'Satisfied'`
- Tolerating: `T ≤ duration < 4T` → `user_category: 'Tolerating'`
- Frustrated: `duration ≥ 4T` → `user_category: 'Frustrated'`
- Span attrs must match Android/iOS contract exactly — read `span.md` for full attribute list

### React `<PulseProvider>` (`src/integrations/react/PulseProvider.tsx`)
- SSR guard: `if (typeof window === 'undefined') return children`
- Init: `useEffect(() => { PulseWeb.start(config) }, [])` — empty dep array = once only
- React StrictMode: the singleton guard in `sdk.ts` handles the double-invocation
- Cleanup: optionally `return () => PulseWeb.shutdown()` for test environments

### `useRouterTracking` hook
- `const location = useLocation()` — `useEffect` on `location.pathname`
- On change: emit `screen_session` span with `screen.name` (current) + `previous_screen.name`
- Resolve `screen.name` via the same 4-step chain from navigation spec (manual → pattern → heuristic → raw path)

### `interaction-config.json` for demo
```json
[{
  "id": "checkout-flow",
  "name": "Checkout",
  "apdex_t": 5000,
  "steps": [
    { "eventName": "checkout_step_1", "operator": "eq", "value": "checkout_step_1" },
    { "eventName": "checkout_step_2", "operator": "eq", "value": "checkout_step_2" },
    { "eventName": "checkout_step_3", "operator": "eq", "value": "checkout_step_3" }
  ]
}]
```

## Done Criteria
- [ ] Interaction span with `user_category` and correct APDEX visible in Pulse Interactions dashboard
- [ ] Config fetch failure → no crash, `interactionsEnabled = false` silently
- [ ] Two concurrent interaction configs tracked independently
- [ ] `sessionSampleRate: 0` in remote config → zero signals exported
- [ ] Feature disabled via remote config → instrumentation not installed on next load
- [ ] React app initialises SDK exactly once in StrictMode (no duplicate exporters)
- [ ] Route changes in demo → `screen_session` span in ClickHouse
- [ ] `<PulseErrorBoundary>` in ErrorDemo → `device.crash` log on render throw
- [ ] No `localStorage is not defined` error in SSR context (SSR guard works)
- [ ] `npm install @dreamhorizon/pulse-web@0.1.0-alpha.1` works in fresh project
- [ ] Unit tests: state machine all transitions, APDEX all 3 bands, sampling=0, feature gate

## Verification

### Unit tests
```bash
cd pulse-web-otel && yarn test --run src/__tests__/m2.test.ts
```

### E2E tests
```bash
cd pulse-web-otel/examples/ecommerce-demo
yarn e2e --grep "@M2" --project=chromium
# Or from SDK root: yarn workspace ecommerce-demo e2e --grep "@M2"
# Covers: checkout APDEX, consent gate, sampling=0, route tracking, SSR guard
```

### Manual + ClickHouse
```bash
yarn build && yarn workspace ecommerce-demo dev
# Navigate to /checkout, click through all 3 steps
```
```sql
SELECT interaction_name, user_category, apdex_score
FROM otel.otel_traces
WHERE pulse_type = 'interaction' AND platform = 'web'
LIMIT 5;
```
Update `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M2 checkboxes when all pass.
