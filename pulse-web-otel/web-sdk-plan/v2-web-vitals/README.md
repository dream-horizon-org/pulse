# Web Vitals — planning package (v2)

Ordered deliverables for introducing **Core Web Vitals** into Pulse Web SDK and related systems.

**Start here for implementation / review:** [**DESIGN.md**](./DESIGN.md) (consolidated architecture, contract, gates, testing, rollout).

Then read in sequence for depth:

| # | Document | Description |
|---|----------|-------------|
| 0 | [DESIGN.md](./DESIGN.md) | **Single design doc** — scope, architecture, contract, touchpoints summary, tests |
| 1 | [01-research-otel-ecosystem-and-industry.md](./01-research-otel-ecosystem-and-industry.md) | OTel conventions, signal types, industry capture, Pulse pipeline |
| 2 | [02-research-otel-js-browser-and-pulse-sdk.md](./02-research-otel-js-browser-and-pulse-sdk.md) | Browser OTel metrics + this repo’s `MeterProvider`, config, registry |
| 3 | [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) | SDK, backend, ingestion, UI, AI — MVP vs later |
| 4 | [ADR-web-vitals.md](./ADR-web-vitals.md) | **Architecture decisions** — metrics-first, attributes, lifecycle |
| 5 | [04-contract-parity.md](./04-contract-parity.md) | Parity with mobile SDKs + **web-only** attributes |
| 6 | [05-implementation-and-test-plan.md](./05-implementation-and-test-plan.md) | Phased implementation + unit/E2E/edge matrix |

**Skills (when executing code):** [`pulse-web-sdk-sanity`](../../../.cursor/skills/pulse-web-sdk-sanity/SKILL.md), [`deploy-service`](../../../.cursor/skills/deploy-service/SKILL.md); optional **web-sdk-guardian** subagent for implementation review.

**Parent meta-plan:** Ordered research → touchpoints → ADR → contract → implementation (*do not skip ADR for coding*).
