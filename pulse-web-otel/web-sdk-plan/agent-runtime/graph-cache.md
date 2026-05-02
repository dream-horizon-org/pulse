# Graph cache digest — pulse-web-otel

**Last sync:** 2026-05-02 (`graphify update . --no-viz` from package root).

**Source of truth:** `pulse-web-otel/graphify-out/GRAPH_REPORT.md` + `graph.json` (639 nodes / 853 edges / 102 communities as of this run).

## Recent focus (Web Vitals / lifecycle close-out)

| Area | Modules |
|------|---------|
| Instrumentation | `src/instrumentations/web-vitals.ts`, `src/instrumentation-registry.ts` |
| Contract | `src/semconv.ts`, `src/feature-gate.ts`, `src/config` types |
| Tests | `src/__tests__/web-vitals-instrumentation.test.ts`, `examples/ecommerce-demo/e2e/web-vitals.spec.ts` |
| Plan | `web-sdk-plan/v2-web-vitals/*` |

## God nodes (navigation)

From latest `GRAPH_REPORT.md`: **`PulseWebSDK`**, **`SessionProvider`**, **`PulseGlobalAttributesProcessor`**, **`InteractionTracker`**, **`ExportSamplingGate`** — highest edge counts; good entry points for blast-radius before refactors.

## How to refresh

```bash
cd pulse-web-otel && graphify update . --no-viz
```

Then update **Last sync** and the **Recent focus** table when you change instrumentation or SDK core.
