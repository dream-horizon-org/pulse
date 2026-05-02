# Web Vitals — planning package (v2)

**Active implementation:** **[Plan B — OTLP logs](PLAN-B-logs-events.md)** (not Plan A metrics). Implementation and review should follow Plan B first; [DESIGN.md](./DESIGN.md) and [ADR-web-vitals.md](./ADR-web-vitals.md) are aligned to Plan B.

**SPA / `screen.name`:** See [PLAN-B — SPA navigation and screen.name accuracy](PLAN-B-logs-events.md) (hard navigation; attrs at callback time).

**Start here for narrative:** [DESIGN.md](./DESIGN.md)

Then read in sequence for depth:

| # | Document | Description |
|---|----------|-------------|
| 0 | [PLAN-B-logs-events.md](./PLAN-B-logs-events.md) | **Primary spec** — emit path, flush, E2E, SQL |
| 1 | [DESIGN.md](./DESIGN.md) | Overview (Plan B) |
| 2 | [ADR-web-vitals.md](./ADR-web-vitals.md) | ADR — logs-first decisions |
| 3 | [04-contract-parity.md](./04-contract-parity.md) | Mobile vs web + log attrs |
| 4 | [01-research-otel-ecosystem-and-industry.md](./01-research-otel-ecosystem-and-industry.md) | Ecosystem research |
| 5 | [02-research-otel-js-browser-and-pulse-sdk.md](./02-research-otel-js-browser-and-pulse-sdk.md) | OTel JS wiring (metrics context; logs path used for Plan B) |
| 6 | [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) | Repo touchpoints |
| 7 | [PLAN-A-metrics-histogram.md](./PLAN-A-metrics-histogram.md) | Deferred alternative |
| 8 | [05-implementation-and-test-plan.md](./05-implementation-and-test-plan.md) | Phased tests (update mentally for Plan B logs where it says metrics) |

**Skills:** [`pulse-web-sdk-sanity`](../../../.cursor/skills/pulse-web-sdk-sanity/SKILL.md), [`deploy-service`](../../../.cursor/skills/deploy-service/SKILL.md).
