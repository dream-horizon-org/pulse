# Graph cache digest — pulse-web-otel

**Last sync:** 2026-05-05 (`graphify update . --no-viz` from package root).

**Source of truth:** `pulse-web-otel/graphify-out/GRAPH_REPORT.md` + `graph.json`.

## Graph summary snapshot

- 646 nodes
- 863 edges
- 99 communities
- God nodes include `PulseWebSDK`, `SessionProvider`, `PulseGlobalAttributesProcessor`

## Recent focus

| Area | Modules |
|------|---------|
| Instrumentation | `src/instrumentations/web-vitals.ts`, `src/instrumentations/errors.ts`, `src/instrumentation-registry.ts` |
| Contract | `src/semconv.ts`, `src/feature-gate.ts`, `src/config` types |
| Tests | `src/__tests__/web-vitals-instrumentation.test.ts`, `examples/ecommerce-demo/e2e/web-vitals.spec.ts`, `examples/ecommerce-demo/e2e/m3-errors.spec.ts` |
| Plan | `web-sdk-plan/v2-web-vitals/*`, `web-sdk-plan/v1-errors/*` |

## Key entities and relations

- `ErrorInstrumentation` -> installs `window.error` and `unhandledrejection` listeners.
- `InstrumentationRegistry` -> maps `errors` to `PulseFeature.JS_CRASH`.
- `m3-errors.spec.ts` -> validates error contract and gate-off behavior.
- `ErrorDemo.tsx` -> provides trigger actions for error scenarios.

## How to refresh

```bash
cd pulse-web-otel && graphify update . --no-viz
```

Then update **Last sync**, **Graph summary snapshot**, and **Recent focus** when instrumentation or SDK core changes.
