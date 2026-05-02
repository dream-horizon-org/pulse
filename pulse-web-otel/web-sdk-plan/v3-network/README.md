# Web SDK plan — v3 Network (HTTP client spans)

## Gap matrix (lifecycle reference.md)

| Row | Status |
|-----|--------|
| A1–A2 Research | **DONE** — `01-*`, `02-*` |
| A3 Touchpoints | **DONE** — `03-touchpoints-matrix.md` |
| A4 Plan A | **N/A** — ADR “Why no Plan A” |
| A5–A9 ADR, PLAN-B, DESIGN, parity, README | **DONE** |
| B1–B6 SDK code | **DONE** — semconv, config, network.ts, registry, sdk getter |
| C Backend template | **N/A** — `network_instrumentation` already in backend enums |
| D Tests / E2E | **DONE** — Vitest + `m4-network.spec.ts` on gates script |
| E Close-out | **DONE** — test-run-log, graphify `--no-viz` |

## Reading order

1. [DESIGN.md](./DESIGN.md)
2. [PLAN-B-network-http-spans.md](./PLAN-B-network-http-spans.md)
3. [ADR-network.md](./ADR-network.md)
4. [04-contract-parity.md](./04-contract-parity.md)

**Legacy detail:** [../v1/02-instrumentations/network.md](../v1/02-instrumentations/network.md)
