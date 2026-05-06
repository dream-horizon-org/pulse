# Web SDK v1 — milestones index

This file is the **canonical entry** for milestone scope, **summarized** exit criteria, and **verification** commands / query patterns. Full checkbox lists, manual TC tables, and edge cases live in the linked `02-instrumentations/*.md` files — do not duplicate every row here.

## M1 — Foundation

- Lifecycle, config, consent, identity: [`01-foundation/`](01-foundation/)
- SDK start / shutdown: `sdk-lifecycle.md` (same folder)

**Exit criteria (summary):** SDK starts only with allowed consent; session + identity attributes match Android parity where documented in foundation docs.

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
- Code: `pulse-web-otel/src/instrumentations/network.ts`, `src/utils/network-http.ts`
- Demo / E2E: `examples/ecommerce-demo/e2e/m4-network.spec.ts`, `examples/ecommerce-demo/src/routes/NetworkLab.tsx`

### M4 exit criteria (summary — see network.md § Done Criteria for full detail)

Authoritative checklist is [`02-instrumentations/network.md`](02-instrumentations/network.md) § **Done Criteria**. Summary:

- Every Fetch and XHR produces a client span with `pulse.type = network.<code>` (or `network.0`) and stable HTTP semconv: `http.request.method`, `url.full`, `http.response.status_code`, `server.address`, etc.
- Pulse OTLP export URLs are excluded from client HTTP tracing.
- Query string stripped from `url.full` by default; optional `captureQueryParams` with sensitive key redaction per `PulseWebSemconv.SensitiveQueryParamName`.
- 4xx/5xx / transport failures set span ERROR + `error.type` where applicable (`4xx`, `5xx`, `network_error`, `cors_error`).
- **Deferred / out of scope for initial ship:** GraphQL body extraction from Fetch (parser exists; async body wiring); `http.request.method = _OTHER` mapping (V2); optional `http.client.request.duration` metric.

---

## Verification

### 1. Automated (required for changes touching `pulse-web-otel/src/`)

```bash
cd pulse-web-otel && yarn vitest run
cd pulse-web-otel/examples/ecommerce-demo && yarn playwright test --config e2e/playwright.config.ts e2e/m4-network.spec.ts --project=chromium   # when network changes
cd pulse-web-otel/examples/ecommerce-demo && yarn e2e:web-sdk-gates   # PR gate
```

Append results to [`../agent-runtime/test-run-log.md`](../agent-runtime/test-run-log.md).

### 2. ClickHouse (when validating full pipeline with real collector)

Use tenant credentials and always filter by project + time range + `LIMIT` (see repo ClickHouse rules).

Example pattern for HTTP client spans (from network.md **TC19**; attribute paths match ingest — confirm materialized columns vs `SpanAttributes` map for your deployment):

```sql
SELECT
  SpanAttributes['pulse.type'] AS pulse_type,
  SpanAttributes['http.request.method'] AS method,
  SpanAttributes['url.full'] AS url_full,
  SpanAttributes['http.response.status_code'] AS status,
  SpanAttributes['error.type'] AS error_type,
  SpanAttributes['graphql.operation.name'] AS gql_name
FROM otel.otel_traces
WHERE SpanAttributes['pulse.type'] LIKE 'network.%'
  AND Timestamp >= now() - INTERVAL 1 HOUR
  AND ProjectId = '<project_uuid>'
ORDER BY Timestamp DESC
LIMIT 50;
```

If your stack exposes **materialized** columns (e.g. `PulseType`, `ProjectId`), prefer those over raw map access per `.claude/rules/clickhouse-sql.md` / backend conventions.

### 3. Graph hygiene

After meaningful `src/` changes, from `pulse-web-otel/`:

```bash
graphify update . --no-viz
```

Then refresh [`../agent-runtime/graph-cache.md`](../agent-runtime/graph-cache.md).

---

## Related plans (v2)

- Clicks buffer / rage: [`../v2-clicks/`](../v2-clicks/)
- Web Vitals metrics/logs: [`../v2-web-vitals/`](../v2-web-vitals/)
