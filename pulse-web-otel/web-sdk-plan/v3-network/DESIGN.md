# DESIGN — Network instrumentation (v3)

## Summary

Automatic **HTTP client spans** for **`fetch`** and **`XMLHttpRequest`**, gated by **`network_instrumentation`**, using OTel browser instrumentations plus Pulse semconv on spans (`pulse.type` = **`network.<statusCode>`**, Android parity).

## Reading order

1. **[ADR-network.md](./ADR-network.md)** — decision summary.
2. **[PLAN-B-network-http-spans.md](./PLAN-B-network-http-spans.md)** — lifecycle, tests, E2E, deferrals.
3. **[../v1/02-instrumentations/network.md](../v1/02-instrumentations/network.md)** — full attribute contract & manual TCs.
4. Phase research: [01](./01-research-network-ecosystem-and-industry.md), [02](./02-research-network-otel-js-browser-and-pulse-sdk.md).
5. [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) — files touched.

## Active plan

**PLAN-B** (`PLAN-B-network-http-spans.md`) is the implementation spec; **no PLAN-A** (see ADR).
