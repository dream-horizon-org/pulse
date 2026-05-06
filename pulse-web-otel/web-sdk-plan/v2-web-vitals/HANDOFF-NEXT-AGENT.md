# Handoff — Web Vitals (v2) / Plan B

**Last updated:** 2026-05-04

## Current implementation (source of truth)

- `InstrumentationConfig.webVitals`: **`enabled?: boolean` only** (no `fid` / `fcp` — FCP, FID, TTFB always registered with LCP/INP/CLS when instrumentation installs).
- `src/instrumentations/web-vitals.ts`: `onLCP`, `onINP`, `onCLS`, `onFCP`, `onFID`, `onTTFB` + `visibilitychange` / `pageshow` flush (private listener refs; `web-vitals` has no cancel API for metric callbacks).
- Remote gate: `PulseFeature` / `featureName` **`web_vitals`** for `pulse_web_js`.
- Plan folder: [PLAN-B-logs-events.md](./PLAN-B-logs-events.md), [ADR-web-vitals.md](./ADR-web-vitals.md).

## If you pause mid-branch

1. Update this file: what landed, what is deferred, copy-paste prompt for the next agent.
2. Append E2E results to [../agent-runtime/test-run-log.md](../agent-runtime/test-run-log.md).

## Next agent prompt (template)

```
Resume Web Vitals (Plan B) in pulse-web-otel. Read v2-web-vitals/PLAN-B-logs-events.md + ADR-web-vitals.md, then web-vitals.ts and e2e/web-vitals.spec.ts. Run: cd pulse-web-otel && yarn test:run && yarn workspace ecommerce-demo e2e:web-sdk-gates
```
