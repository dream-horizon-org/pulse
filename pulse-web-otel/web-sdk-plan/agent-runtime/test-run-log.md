# Web SDK Test Run Log (Append Only)

Rules:
- Append only; never rewrite past entries.
- Keep entries concise and non-verbose.
- Include: timestamp, command, files/suites, passed, failed, failed test files/specs.

---

## 2026-04-27T17:41:35+05:30
- Command: `yarn vitest run src/__tests__/interaction-instrumentation.test.ts src/__tests__/interactions-sdk-wiring.test.ts src/__tests__/sdk-lifecycle.test.ts`
- Files/Suites: interaction instrumentation + SDK wiring + SDK lifecycle
- Passed: 16
- Failed: 0
- Failed test files/specs: none

## 2026-04-27T18:32:34+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts e2e/m2-interactions-edge.spec.ts`
- Files/Suites: `e2e/m1.spec.ts`, `e2e/m2-interactions-edge.spec.ts` (Chromium)
- Passed: 97
- Failed: 12
- Failed test files/specs:
  - `e2e/m1.spec.ts:226` installation.id fallback to sessionStorage
  - `e2e/m1.spec.ts:244` installation.id fallback to in-memory
  - `e2e/m1.spec.ts:415` batching coalesced logs payload
  - `e2e/m1.spec.ts:459` first export after batch delay
  - `e2e/m1.spec.ts:942` metering header stable across requests
  - `e2e/m1.spec.ts:1097` window.id unique per page load
  - `e2e/m1.spec.ts:1131` window.id stable within one page load
  - `e2e/m1.spec.ts:1298` screen.name strips numeric segment
  - `e2e/m1.spec.ts:1330` screen.name strips UUID segment
  - `e2e/m1.spec.ts:1399` screen.name reset after navigation
  - `e2e/m1.spec.ts:1457` url.path after SPA navigation
  - `e2e/m1.spec.ts:1673` fresh browser context independent session

## 2026-04-27T19:02:00+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts --grep "installation.id falls back|multiple trackEvent calls coalesced|first export happens after batch delay|X-Pulse-Metering-Session-ID is stable|window.id is unique per page load|window.id same across multiple signals|screen.name strips numeric segment|screen.name strips UUID segment|screen.name resets to URL path after navigation|url.path updates correctly after SPA navigation|fresh browser context creates new independent session"`
- Files/Suites: `e2e/m1.spec.ts` focused subset (Chromium)
- Passed: 1
- Failed: 11
- Failed test files/specs:
  - `e2e/m1.spec.ts:226`
  - `e2e/m1.spec.ts:244`
  - `e2e/m1.spec.ts:415`
  - `e2e/m1.spec.ts:459`
  - `e2e/m1.spec.ts:942`
  - `e2e/m1.spec.ts:1097`
  - `e2e/m1.spec.ts:1131`
  - `e2e/m1.spec.ts:1298`
  - `e2e/m1.spec.ts:1399`
  - `e2e/m1.spec.ts:1457`
  - `e2e/m1.spec.ts:1673`

## 2026-04-27T19:05:00+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts --grep "installation.id falls back|multiple trackEvent calls coalesced|first export happens after batch delay|X-Pulse-Metering-Session-ID is stable|window.id is unique per page load|window.id same across multiple signals|screen.name strips numeric segment|screen.name strips UUID segment|screen.name resets to URL path after navigation|url.path updates correctly after SPA navigation|fresh browser context creates new independent session"`
- Files/Suites: `e2e/m1.spec.ts` focused subset after env+SDK fix (Chromium)
- Passed: 8
- Failed: 4
- Failed test files/specs:
  - `e2e/m1.spec.ts:1097`
  - `e2e/m1.spec.ts:1330`
  - `e2e/m1.spec.ts:1399`
  - `e2e/m1.spec.ts:1457`

## 2026-04-27T19:07:00+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts --grep "installation.id falls back|multiple trackEvent calls coalesced|first export happens after batch delay|X-Pulse-Metering-Session-ID is stable|window.id is unique per page load|window.id same across multiple signals|screen.name strips numeric segment|screen.name strips UUID segment|screen.name resets to URL path after navigation|url.path updates correctly after SPA navigation|fresh browser context creates new independent session"`
- Files/Suites: `e2e/m1.spec.ts` focused subset after test hardening (Chromium)
- Passed: 12
- Failed: 0
- Failed test files/specs: none

## 2026-04-27T19:30:00+05:30
- Command: `yarn test:run src/__tests__/sdk-lifecycle.test.ts src/__tests__/interactions-sdk-wiring.test.ts`
- Files/Suites: `sdk-lifecycle`, `interactions-sdk-wiring`
- Passed: 12
- Failed: 0
- Failed test files/specs: none

## 2026-04-27T19:19:00+05:30
- Command: `yarn test:run src/__tests__/m1.test.ts src/__tests__/integration-simplified-init.test.ts src/__tests__/sdk-lifecycle.test.ts src/__tests__/interactions-config-fetcher.test.ts src/__tests__/interactions-coordinator.test.ts src/__tests__/interactions-events-utils.test.ts src/__tests__/interaction-feature-integration.test.ts src/__tests__/interaction-feature.test.ts src/__tests__/interaction-instrumentation.test.ts src/__tests__/interactions-sdk-wiring.test.ts src/__tests__/interactions-sequence-matcher.test.ts src/__tests__/interactions-span-builder.test.ts src/__tests__/interactions-tracker.test.ts src/__tests__/exporters-batch-queue.test.ts`
- Files/Suites: foundation + interaction-focused unit suites
- Passed: 198
- Failed: 0
- Failed test files/specs: none

## 2026-04-27T19:22:00+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts e2e/m2-interactions.spec.ts e2e/m2-interactions-edge.spec.ts`
- Files/Suites: `e2e/m1.spec.ts`, `e2e/m2-interactions.spec.ts`, `e2e/m2-interactions-edge.spec.ts` (Chromium)
- Passed: 124
- Failed: 2
- Failed test files/specs:
  - `e2e/m1.spec.ts:1508` page.url is full URL and url.path is path-only — two separate attributes (`window.PulseWeb.trackEvent` undefined in page.evaluate)
  - `e2e/m2-interactions.spec.ts:453` interaction config fetch unavailable -> no interaction span, sdk still running (expected 0 interaction spans, got 1)

## 2026-04-27T20:01:00+05:30
- Command: `yarn workspace ecommerce-demo e2e --project=chromium e2e/m1.spec.ts --grep "page.url is full URL and url.path is path-only" && yarn workspace ecommerce-demo e2e --project=chromium e2e/m2-interactions.spec.ts --grep "interaction config fetch unavailable -> no interaction span, sdk still running"`
- Files/Suites: targeted rerun after `.env.test` fix for interaction mock preload
- Passed: 2
- Failed: 0
- Failed test files/specs: none

## 2026-04-27T22:30:00Z
- Note: `m2-interactions-edge.spec.ts` merged into `m2-interactions.spec.ts` (second `test.describe("@M2 interactions edge cases")`); edge file removed. Web SDK PR gate: `yarn workspace ecommerce-demo e2e:web-sdk-gates` (`examples/ecommerce-demo/package.json`).
- Command: `yarn workspace ecommerce-demo e2e:web-sdk-gates`
- Files/Suites: `e2e/m1.spec.ts`, `e2e/m2-interactions.spec.ts` (Chromium)
- Passed: 126
- Failed: 0
- Failed test files/specs: none
