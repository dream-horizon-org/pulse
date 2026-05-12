# clickhouse-evaluation (delegated)

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Document the evaluation contract. The actual ClickHouse query lives in
`backend/server/` (pulse-server), not in this service.

## 2. Contract

- `CronManager` POSTs:
  `{pulseServerUrl}/v1/alert/evaluateAndTriggerAlert?alertId={id}`.
- pulse-server reads the alert config (MySQL), builds the ClickHouse
  query (must include time-range on `Timestamp`, `LIMIT`,
  `ProjectId` filter — see repo CLAUDE.md ClickHouse rules), executes
  via tenant credentials (row policies enforce isolation), compares
  result against threshold, and writes evaluation history + dispatches
  notifications.

## 3. Materialized columns (must use, never map access)

| Column     | Source                                    |
| ---------- | ----------------------------------------- |
| ProjectId  | `ResourceAttributes['project.id']`        |
| PulseType  | `Span/LogAttributes['pulse.type']`        |
| Platform   | `ResourceAttributes['os.name']`           |
| AppVersion | `ResourceAttributes['app.build_name']`    |
| SessionId  | `Span/LogAttributes['session.id']`        |

## 4. Why split this way

- Single ClickHouse client pool (in pulse-server) → easier tenant
  credential rotation.
- This cron service stays stateless; horizontal scale is trivial once
  HA scheduling is added.

## 5. Failure semantics

- 5xx from pulse-server → `CronManager` retries with exponential
  backoff (see [cron-manager](./cron-manager.md) §5).
- 4xx (e.g. alert deleted) → no retry; surfaced in logs.

## 6. If we ever inline evaluation

We would need: ClickHouse JDBC/HTTP client, tenant credential
resolution, row-policy-aware query builder, notification channel
clients — i.e. effectively port the alert subsystem of `backend/server/`.
Out of scope for now.

## 7. Tests

Smoke: assert the POST URL shape (`AlertsService` test) — the rest is
covered in pulse-server.

## 8. Cross-links

- Caller: [cron-manager](./cron-manager.md)
- Config source: [alerts-service](./alerts-service.md)

## 9. Open items

- Add `evaluationLatencyMs` header from pulse-server for cron-side
  metric.
- Consider streaming evaluation results so we can detect partial
  pulse-server outages faster.
