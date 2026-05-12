# ClickHouse — row policies (per-project isolation)

## Purpose

Enforce that a project's ClickHouse user can only read its own rows. Defence in depth: even if a query forgets the `ProjectId` filter, the row policy adds it.

## Source

Row policies are **NOT** defined in `backend/db/*/clickhouse/*.sql`. They are managed at runtime by `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/ClickhouseProjectService.java` (tested in `ClickhouseProjectServiceTest.java`).

## Mechanics

For each project the backend provisions:
1. A dedicated CH user (credentials stored in MySQL `clickhouse_project_credentials`, audited via `clickhouse_project_credential_audit`).
2. One `ROW POLICY` per relevant table, of the form `WHERE ProjectId = '<project_id>'`, granted to that user.

The backend connects to ClickHouse with the project user when serving tenant queries. Admin / OTel-Collector / Vector users are excluded from the row-policy `TO …` clause so writes are unaffected.

## Tables covered

At minimum: `otel_logs`, `otel_traces`, `otel_metrics_*`, `stack_trace_events`, `session_replay_events`, `interaction_heatmaps_daily`, `funnel_results`, `journey_results`, `root_cause_cache`, `screen_root_cause_cache`, `event_catalog_entries`, `session_summary`. The list is implemented in code (`ClickhouseProjectService`); always treat that file as the source of truth.

## Inputs

- Project creation flow in `pulse-server` → `ClickhouseProjectService.createProjectUser(...)` → `CREATE USER`, `CREATE ROW POLICY`, `GRANT`.
- Credential rotation → updates MySQL row + CH user password, writes audit row.

## Outputs

- Every tenant-scoped query in dashboard / alerts / AI / RCA paths goes through the project's CH user; row policies filter implicitly.

## Operational notes

- The admin user (`CLICKHOUSE_USER` from `.env`) is for ops only. Never wire application code to it.
- Adding a new tenant-readable table: extend `ClickhouseProjectService` so policies are issued on next provisioning cycle, and backfill existing projects with a one-shot script.

## Failure modes

- "Failed to execute tenant query / Authentication failed" → password drift; fix with `deploy/scripts/sync-default-tenant-ch-credentials.py`.
- New table added in SQL but not registered in `ClickhouseProjectService` → tenant queries return empty (no grants) or leak (if app uses admin user — don't).

## Related

- `clickhouse/materialized-columns.md` — `ProjectId` is the filter column.
- `mysql/tenants-projects.md` — `clickhouse_project_credentials` lifecycle.

## Open questions

- No periodic reconciler verifying CH state matches MySQL credentials table — drift is detected only on first failing query.
