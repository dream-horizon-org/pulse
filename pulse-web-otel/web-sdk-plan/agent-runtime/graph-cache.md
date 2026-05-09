# Graph cache digest — pulse-web-otel

**Last sync:** 2026-05-06 (`graphify update . --no-viz` from `pulse-web-otel/`).

**Source of truth:** `pulse-web-otel/graphify-out/GRAPH_REPORT.md` + `graph.json`.

## Graph summary snapshot

- 763 nodes
- 1018 edges
- 113 communities
- God nodes include `PulseWebSDK`, `SessionProvider`, `PulseGlobalAttributesProcessor`

## Recent focus

| Area | Modules |
|------|---------|
| Instrumentation | `src/instrumentations/network.ts`, `src/utils/network-http.ts`, `src/instrumentation-registry.ts` |
| Contract | `src/semconv.ts`, `src/types/config.ts` |
| Tests | `src/__tests__/network-http.test.ts`, `src/__tests__/network-instrumentation.test.ts`, `examples/ecommerce-demo/e2e/m4-network.spec.ts` |
| Plan | `web-sdk-plan/v1/MILESTONES.md`, `web-sdk-plan/v1/02-instrumentations/network.md`, `web-sdk-plan/v3-network/*` |

## Key entities and relations

- `NetworkInstrumentation` -> installs OTel `FetchInstrumentation` + `XMLHttpRequestInstrumentation`; `applyPulseHttpClientSpanAttributes` stamps `pulse.type` + stable HTTP attrs.
- `InstrumentationRegistry` -> owns install/uninstall; `NetworkInstrumentation` idempotent `install()` when already active.
- `m4-network.spec.ts` -> Network Lab + probe URLs; XHR timeout/abort use `page.route` stall on `https://httpstat.us/**` so OTLP sees `network.0` + `network_error`.

## How to refresh

```bash
cd pulse-web-otel && graphify update . --no-viz
```

Then update **Last sync**, **Graph summary snapshot**, and **Recent focus** when instrumentation or SDK core changes.
