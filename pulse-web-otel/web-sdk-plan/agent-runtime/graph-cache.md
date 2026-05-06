# Graph cache digest

- Last sync: 2026-05-05
- Graph source artifacts:
  - `pulse-web-otel/graphify-out/GRAPH_REPORT.md` -> present
  - `pulse-web-otel/graphify-out/graph.json` -> present
- Graph summary snapshot:
  - 646 nodes
  - 863 edges
  - 99 communities
  - God nodes include `PulseWebSDK`, `SessionProvider`, `PulseGlobalAttributesProcessor`
- Areas touched in this run:
  - error instrumentation E2E
  - ecommerce demo error route
  - web SDK error lifecycle docs
- Key entities and relations (code-derived, not graph-generated):
  - `ErrorInstrumentation` -> installs `window.error` and `unhandledrejection` listeners
  - `InstrumentationRegistry` -> maps `errors` to `PulseFeature.JS_CRASH`
  - `m3-errors.spec.ts` -> validates error contract and gate-off behavior
  - `ErrorDemo.tsx` -> provides trigger actions for error scenarios

