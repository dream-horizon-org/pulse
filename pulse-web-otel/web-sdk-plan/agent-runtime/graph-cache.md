# Web SDK Graph Cache

Use:
- Fast digest of generated Graphify outputs for agent decision-making.
- Do not treat this file as a replacement for code truth.

Format:
- Synced at: ISO timestamp
- Source graph version/date
- Touched areas
- Key relations relevant to current tasks
- Open parity flags (if any)

---

Synced at: 2026-04-27T14:00:00Z
Source: `pulse-web-otel/graphify-out/GRAPH_REPORT.md` + `pulse-web-otel/graphify-out/graph.json` (from `graphify update src`)
Touched areas: SDK startup orchestration refactor (`finishStart` decomposition), session persistence fallback, ecommerce-demo M1 E2E stability, graph workflow docs
Key relations:
- `sdk.ts` owns startup/shutdown orchestration
- `InstrumentationRegistry` owns install/uninstall order
- `InteractionInstrumentation` wraps interaction feature lifecycle
- `trackEvent()` -> instrumentation adapter -> interaction matcher/tracker pipeline
- `PulseGlobalAttributesProcessor` and `SessionProvider` are central shared dependencies
- `finishStart` remains single-owner startup flow but now delegates to scoped helper methods
- `SessionProvider` + identity helpers must not throw when storage APIs are unavailable
- `m1.spec.ts` route/reload assertions depend on deterministic active-config behavior and init readiness checks
Open parity flags:
- Monitor browser-parity gaps where Firefox/WebKit E2E is not regularly executed
