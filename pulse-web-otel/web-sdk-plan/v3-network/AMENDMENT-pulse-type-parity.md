# Amendment — `pulse.type` parity: Web → `network.<code>`

**Status:** IMPLEMENTED (Web SDK `pulse-web-otel`)  
**Supersedes:** ADR-network.md decision line (`pulse.type: http`) — replaced by `network.<statusCode>`  
**Discovered by:** web-sdk-guardian cross-platform audit (2026-05-03)

---

## Problem

Web currently emits `pulse.type = "http"` on every network span.  
Android emits `pulse.type = "network.<statusCode>"` (e.g. `network.200`, `network.404`, `network.0`).

The backend ClickHouse queries **only** use `LIKE 'network.%'` predicates:

```java
// ClickhouseConstants.java
CH_PULSE_TYPE_NETWORK_LIKE_PREDICATE = "PulseType LIKE 'network.%'"
NET_0              = "countIf(PulseType = 'network.0')"
NET_2XX            = "countIf(PulseType LIKE 'network.2%')"
NET_4XX            = "countIf(PulseType LIKE 'network.4%')"
NET_COUNT          = "countIf(PulseType LIKE 'network.%')"
ERROR_RATE_4XX     = "(countIf(PulseType LIKE 'network.4%') / countIf(PulseType LIKE 'network.%')) * 100"
```

Web spans with `pulse.type = "http"` **match none of these**. They are silently excluded from every network dashboard, error rate, and alert query.

---

## Industry research context

| Tool | Discriminator key | Web value | Android value | Shared backend? |
|------|------------------|-----------|---------------|-----------------|
| Sentry | `span.op` | `"http.client"` | `"http.client"` | ✅ same value, same table |
| Datadog RUM | `resource.type` | `"xhr"` / `"fetch"` | `"native"` | ❌ separate pipelines per platform |
| New Relic | event type | `AjaxRequest` | `MobileRequest` | ❌ separate tables |
| PostHog | event name | `$performance_event` | proprietary | ❌ no shared query model |
| **Pulse** | `pulse.type` | `"http"` ← wrong | `"network.200"` | ✅ one `otel_traces` table |

Datadog/New Relic can afford different discriminators because they have **separate pipelines per platform**.  
Pulse shares one ClickHouse table — the Sentry model (same value everywhere) is the right reference.

**The OTel attribute names (`http.request.method`, `url.full`, `http.response.status_code`, `server.address`) are already aligned across platforms. Only the `pulse.type` discriminator is wrong.**

---

## Android source of truth

From `PulseSdkSignalProcessors.kt`:

```kotlin
PulseOtelUtils.isNetworkSpan(span) -> {
    PULSE_NETWORK.getAttributeKey(
        span.attributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE)?.toString()
            ?: span.attributes.get(HttpIncubatingAttributes.HTTP_STATUS_CODE)?.toString()
            ?: "0"   // ← fallback when no status (network failure, CORS, abort)
    ).key
}
// PULSE_NETWORK = stringKeyTemplate("network")
// → PULSE_NETWORK.getAttributeKey("200").key = "network.200"
// → PULSE_NETWORK.getAttributeKey("0").key   = "network.0"
```

Confirmed by `PulseSignalProcessorTest.kt`:
- No status (network failure): `"network.0"`
- HTTP 200: `"network.200"`
- HTTP 404: `"network.404"`

`isNetworkType(type)` = `type.startsWith("network.")` — the matching predicate on Android.

---

## Decision needed

### D1 — Confirm value change ✅ (recommended)

Change Web `pulse.type` from `"http"` to `"network.<statusCode>"`.

**Fallback for no-status** (CORS block, network failure, abort): `"network.0"` — matches Android exactly.

**Not changing:** All other attributes (`http.request.method`, `url.full`, `http.response.status_code`, `error.type`, `http.duration`, etc.) stay as-is. This is a one-field change.

**Backend impact:** None. Backend already queries `LIKE 'network.%'` — adding Web spans makes them *visible* for the first time, it doesn't break existing Android data.

**Why not extend backend instead?** Every future dashboard written as `LIKE 'network.%'` would miss Web forever. Web SDK is pre-production — blast radius is bounded to `pulse-web-otel/`.

---

### D2 — `PulseType.HTTP` in `semconv.ts`

Current: `HTTP: "http"` (static string constant).

Options:
- **A (recommended):** Remove `PulseType.HTTP`. Add `networkPulseType(statusCode?: number): string` exported from `src/utils/network-http.ts`. No constant needed — the value is dynamic.
- **B:** Keep `PulseType.HTTP` but rename to `PulseType.NETWORK` = `"network"` as a base prefix. Utility function still needed for full `network.<code>` value.

Option A is cleaner — the value was never a static string on Android either.

---

### D3 — `fixture.ts` helper for E2E assertions

`findAllSpans(captured, "http")` does **exact** `pulse.type` match. After the change, each span will have a different `pulse.type` value (`"network.200"`, `"network.404"`, etc.).

Options:
- **A (recommended):** Add `findAllNetworkSpans(captured)` to `fixture.ts` — prefix-matches `pulse.type.startsWith("network.")`, excluding `"network.change"`.
- **B:** Replace per-test with specific code strings (e.g. `findAllSpans(captured, "network.200")`).

Option A is cleaner for multi-test reuse. Option B is fine for the two tests that assert a single known status.

---

## What changes (scope)

### Code

| File | Change |
|------|--------|
| `src/semconv.ts` | Remove `PulseType.HTTP = "http"` |
| `src/utils/network-http.ts` | Add exported `networkPulseType(statusCode?: number): string` → `"network.${code}"` with `"network.0"` fallback |
| `src/utils/network-http.ts` | Update `applyPulseHttpClientSpanAttributes` to call `networkPulseType(statusCode)` |
| `src/__tests__/network-http.test.ts` | Update unit test: `expect(attrs["pulse.type"]).toBe("network.200")` |
| `examples/ecommerce-demo/e2e/fixture.ts` | Add `findAllNetworkSpans(captured)` prefix-match helper |
| `examples/ecommerce-demo/e2e/m4-network.spec.ts` | Replace `findAllSpans(captured, "http")` with `findAllNetworkSpans(captured)`; update `pulse.type` assertions from `"http"` to `"network.<code>"` |

### Docs (this folder)

| File | Change |
|------|--------|
| `ADR-network.md` | Update decision line: `pulse.type: network.<statusCode>` |
| `04-contract-parity.md` | Fix "Aligned" row: Android = `network.<code>`, Web = `network.<code>` (after fix) |
| `01-research-network-ecosystem-and-industry.md` | Add vendor discriminator comparison section |
| `v1/02-instrumentations/network.md` | Update attribute table: `pulse.type` row → `network.<statusCode>` |
| `PLAN-B-network-http-spans.md` | Update E2E outline: replace `http` references with `network.<code>` |

### No backend changes needed.

---

## Resolution (implementation)

1. **D2:** `PulseType.HTTP` removed; `networkPulseType()` in `src/utils/network-http.ts`.
2. **D3:** `findAllNetworkSpans()` added to `examples/ecommerce-demo/e2e/fixture.ts`.
3. **`error.type`:** unchanged — additive web extension; Android relies on span status.
