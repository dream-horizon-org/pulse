# PLAN-D — Context snapshot & open questions

**Purpose:** Single place to resume work on **`http.client.request.duration`** without re-reading the full thread. Canonical spec: **[`PLAN-D-http-client-request-duration.md`](./PLAN-D-http-client-request-duration.md)**.

**Status:** Snapshot for pickup — update when decisions land.

---

## What PLAN-D adds

| Topic | Detail |
|-------|--------|
| **Instrument** | OTel stable histogram **`http.client.request.duration`**, unit **seconds**, batch → `otel_metrics_histogram`. |
| **vs span `http.duration`** | Span attr is **ms** on traces; histogram is for **aggregated percentiles** (p50/p95/p99) without scanning spans. |
| **Gate** | Config only: **`instrumentations.network.emitRequestDurationMetric`** (already reserved in `types/config.ts`). Default **false**. No new `PulseFeature` enum. |

---

## Three decisions already locked (PLAN-D)

| ID | Decision |
|----|----------|
| **D1 — Meter** | **`metrics.getMeter("pulse-web")`** via OTel **global** API inside `NetworkInstrumentation.install()`. **No `SdkContext` change.** Rationale: `sdk.ts` sets **`metrics.setGlobalMeterProvider(...)`** before **`installAll()`** (~L323 → ~L339), so global exists when `install()` runs. |
| **D2 — Duration** | Reuse **`resourceTimingDurationMs(perfKey)`** → convert **ms → s** for `histogram.record`. If timing is **absent** (opaque response, CORS, Playwright stub): **do not record** — no wall-clock fallback. |
| **D3 — Buckets** | OTel recommended boundaries (seconds), **hardcoded** for v1 — same spirit as web-vitals histograms. |

---

## Intended touchpoints (implementation checklist)

| File | Role |
|------|------|
| [`src/instrumentations/network.ts`](../../src/instrumentations/network.ts) | Private **`_durationHistogram`**; create when **`emitRequestDurationMetric === true`**; **`record`** in Fetch + XHR callbacks after span attrs; **`undefined`** on **`uninstall()`**. |
| [`src/utils/network-http.ts`](../../src/utils/network-http.ts) | No structural change — reuse duration / attrs already computed for spans. |
| [`examples/ecommerce-demo/src/App.tsx`](../../examples/ecommerce-demo/src/App.tsx) | Demo: e.g. **`?pulse_network_metric=1`** merges **`emitRequestDurationMetric: true`** into Pulse init (mirror **`pulse_network_enabled`** pattern). |
| [`examples/ecommerce-demo/e2e/m4-network.spec.ts`](../../examples/ecommerce-demo/e2e/m4-network.spec.ts) | **M1** positive (flag on → metric export); **M2** negative (flag off → zero metric points). Use **`findAllMetricPoints` / `waitForMetric`** from [`fixture.ts`](../../examples/ecommerce-demo/e2e/fixture.ts). |
| Vitest | Mock **`metrics.getMeter`**; assert **`record`** / no-record when duration missing; **`uninstall`** clears histogram ref. |

---

## Open questions — resolve before / during E2E

### 1. Playwright vs `PerformanceResourceTiming` (blocking for **M1** design)

**Question:** Does the probe request get a real **`PerformanceResourceTiming`** entry in our setup?

- **`page.route` + `route.fulfill()`** often **does not** yield usable Resource Timing (or **`resourceTimingDurationMs`** returns **`undefined`**).
- If so, **`histogram.record` never runs** even with **`emitRequestDurationMetric: true`** — M1 would **flake or always fail**.

**Resolution options (pick one before locking M1):**

1. **`route.continue()`** so the request hits the **real Vite dev server** (timing more likely).
2. **Small real HTTP server** in Playwright **globalSetup** / fixture (explicit timing).
3. **Weaker M1:** assert metric **only if** a point appears within timeout; **`test.skip`** / document when CI has no timing (honest deferral).

**Action:** Validate D0a — run a one-off Playwright snippet: **`page.route` fulfill vs continue`** and log **`performance.getEntriesByName(response.url)`** after fetch.

### 2. Secondary (implementation hygiene)

- **Meter name** **`"pulse-web"`** must match whatever **`setGlobalMeterProvider`** / meter registration uses elsewhere — grep before shipping.
- **Batch + export:** align **`waitForMetric`** timeout with **`VITE_PULSE_BATCH_DELAY_MS`** and existing **`pagehide`** flush behavior (`sdk.ts`).

---

## Quick links

- Full plan: [`PLAN-D-http-client-request-duration.md`](./PLAN-D-http-client-request-duration.md)
- Prior deferral / config key: [`PLAN-C-otel-spec-alignment.md`](./PLAN-C-otel-spec-alignment.md) §P3.5
- Network ADR: [`ADR-network.md`](./ADR-network.md)
