# M2 Interactions Exit Verification

Date: 2026-04-25
Scope: Phases 1-7 from `web_sdk_interactions_plan_74dfd441.plan.md` (interactions slice)

## Verification Commands

- Unit interactions suite:
  - `yarn test:run src/__tests__/interactions-config-fetcher.test.ts src/__tests__/interactions-tracker.test.ts src/__tests__/interactions-coordinator.test.ts src/__tests__/interactions-span-builder.test.ts src/__tests__/interactions-sdk-wiring.test.ts src/__tests__/interaction-feature.test.ts src/__tests__/interaction-feature-integration.test.ts src/__tests__/interactions-sequence-matcher.test.ts src/__tests__/interactions-events-utils.test.ts`
  - Result: 9 files, 44 tests, all passing
- E2E interactions suite (includes `@M2 interactions edge cases` in the same file):
  - `yarn workspace ecommerce-demo e2e:m2-interactions` or full gate `yarn workspace ecommerce-demo e2e:web-sdk-gates`
  - Legacy: `yarn e2e --project=chromium e2e/m2-interactions.spec.ts` from `examples/ecommerce-demo/`

## Done-Criteria Coverage Map

### Config (`03-interactions/config.md`)

- Local config fetch + cache + refresh + resilience guards: covered by `interactions-config-fetcher` unit suite.
- Fetch failure fallback/no crash behavior: covered by unit suite and E2E `interaction config fetch unavailable`.

Status: Complete

### Matching (`03-interactions/matching.md`)

- Ordered matching, timeout, sequence violation, global blacklist reset, property operators: covered by matcher/tracker/coordinator/unit suites and E2E error-path cases.
- `NOTCONTAINS` operator coverage: included in matcher unit suite.
- Parallel multi-config completion covered by coordinator interleaved-events test.
- Positive APDEX band scoring (`Excellent`, `Good`, `Average`, `Poor`) covered by matcher band test with fixed thresholds.

Status: Complete

### Span Contract (`03-interactions/span.md`)

- `pulse.interaction.*` attr contract, ROOT_CONTEXT, event timeline, error coercion (`Poor`, `0.0`), timestamp derivation: covered by `interactions-span-builder` unit suite.
- `complete_time` nanos invariant: covered by E2E interaction span invariant case.
- Explicit no-`pulse.internal.*` export assertion added in span-builder suite.

Status: Complete

### SDK Wiring (`src/sdk.ts` + `interaction-feature.ts`)

- `trackEvent(name, attrs, timestampMs)` forwarding: covered by `interactions-sdk-wiring`.
- Feature gate disabled path: covered by `interactions-sdk-wiring` and `interaction-feature`.
- Consent denied path: covered by `interactions-sdk-wiring`.
- Sampling-drop wiring path: covered by `interactions-sdk-wiring`.
- Interaction feature teardown on shutdown: covered by `interactions-sdk-wiring` + `interaction-feature`.
- Non-browser integration pipeline (`fetcher -> coordinator -> tracker -> matcher -> span builder`) covered by `interaction-feature-integration` test with tracer stub.

Status: Complete

## M2 Milestone Gate Mapping (`MILESTONES.md`)

- Interaction span with category/APDEX exported: Verified in E2E pass.
- `pulse.type=interaction` and resource `platform=web` explicitly asserted in interactions E2E.
- Timeout/error behavior: Verified in E2E pass.
- Config fetch failure graceful behavior: Verified in E2E pass.
- Session sample-rate export drop wiring: Verified in unit pass.
- Unit tests for state machine/gating: Verified in unit pass.

Status: Complete for automated gates

## Manual Gate

- Manual dashboard verification required by plan:
  - Trigger known interaction flows.
  - Open Interactions dashboard.
  - Confirm category/filter values appear for `Excellent`, `Good`, `Average`, `Poor`.

Status: Pending manual verification in dashboard environment.

## Deliverables Added for Phase 6-7

- `web-sdk-plan/v1/WEB-SDK Interactions test coverage (M2).csv`
- `web-sdk-plan/v1/M2-INTERACTIONS-EXIT-VERIFICATION.md`
