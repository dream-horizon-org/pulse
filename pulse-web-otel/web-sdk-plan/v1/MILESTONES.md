# Web SDK v1 — milestones index

Thin index for agents and humans. Detailed specs live in linked folders.

## M1 — Foundation

- Lifecycle, config, consent, identity: [`01-foundation/`](01-foundation/)
- SDK start / shutdown: `sdk-lifecycle.md` (same folder)

## M2 — Core instrumentations

| Area | Doc |
|------|-----|
| Clicks | [`02-instrumentations/clicks.md`](02-instrumentations/clicks.md) |
| Errors / non-fatals | [`02-instrumentations/errors.md`](02-instrumentations/errors.md) |
| Web Vitals | [`02-instrumentations/web-vitals.md`](02-instrumentations/web-vitals.md) |
| Screens / navigation | [`02-instrumentations/navigation.md`](02-instrumentations/navigation.md) |
| **Network (Fetch + XHR)** | [`02-instrumentations/network.md`](02-instrumentations/network.md) |

## M3 — Errors program (cross-links)

- [`../v1-errors/`](../v1-errors/) — research, ADR, contract parity, PLAN-B

## M4 — Network program

- [`../v3-network/`](../v3-network/) — PLAN-B HTTP spans, PLAN-C OTel alignment, contract parity
- Implementation: `pulse-web-otel/src/instrumentations/network.ts`, `src/utils/network-http.ts`
- E2E harness: `examples/ecommerce-demo/e2e/m4-network.spec.ts`, `src/routes/NetworkLab.tsx`

## Exit criteria and verification

- Per-instrumentation **Done Criteria** sections in each `02-instrumentations/*.md` (e.g. network.md § Done Criteria).
- **Agent runtime:** append commands + outcomes to [`../agent-runtime/test-run-log.md`](../agent-runtime/test-run-log.md).
- **Graph:** after meaningful `src/` changes, from `pulse-web-otel/`: `graphify update . --no-viz` then refresh [`../agent-runtime/graph-cache.md`](../agent-runtime/graph-cache.md).

## Related plans (v2)

- Clicks buffer / rage: [`../v2-clicks/`](../v2-clicks/)
- Web Vitals metrics/logs: [`../v2-web-vitals/`](../v2-web-vitals/)
