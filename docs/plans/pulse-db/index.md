# pulse-db — plan index

Deep-dive plan for the MySQL control plane and ClickHouse telemetry store. Companion brief: `/Users/ujjwal.bagrania/Desktop/pulse/docs/components/pulse-db.md`.

## Scope split

- **MySQL** — `backend/db/{dev,prod}/mysql/mysql-init.sql`. Single bootstrap script per env. Tables grouped by subsystem in sub-plans below.
- **ClickHouse** — `backend/db/{dev,prod}/clickhouse/*.sql`. One file per table family in prod; numbered prefix in dev for ordered apply.

## Sub-plans

### MySQL
- [mysql/schema-overview.md](mysql/schema-overview.md) — full table inventory, FK graph, charset, dev seed.
- [mysql/tenants-projects.md](mysql/tenants-projects.md) — `tenants`, `projects`, `users`, API keys, tiers, usage limits.
- [mysql/alerts.md](mysql/alerts.md) — `alerts`, `alert_scope`, `alert_metrics`, `scope_types`, `alert_evaluation_history`, `severity`, `incidents`, `notification_*`.
- [mysql/event-definitions.md](mysql/event-definitions.md) — `event_definitions`, `event_attribute_definitions`, `interaction`, `suggested_interaction`, `pulse_sdk_configs`, `funnel`, `journey`.
- [mysql/migrations.md](mysql/migrations.md) — how dev/prod diverge, reset workflow, schema-change protocol.

### ClickHouse
- [clickhouse/otel-tables.md](clickhouse/otel-tables.md) — `otel_logs`, `otel_traces`, `otel_metrics_*`, `stack_trace_events`, plus Pulse analytics tables (heatmaps, replay, funnel/journey results, root-cause caches, project_monthly_usage, event_catalog_entries, session_summary).
- [clickhouse/materialized-columns.md](clickhouse/materialized-columns.md) — exhaustive list of materialized columns and their source map keys; query-time rules.
- [clickhouse/row-policies.md](clickhouse/row-policies.md) — runtime-managed per-project row policies (provisioned by `ClickhouseProjectService`).
- [clickhouse/migrations.md](clickhouse/migrations.md) — bootstrap-only model, dev numbering, prod file-per-table, `init-clickhouse.sh`.

## Authoritative rules

1. Always query CH materialized columns, never `Attributes['x']` map access.
2. Every CH query must include `Timestamp` range + `ProjectId` filter + `LIMIT`.
3. Tenants connect with their per-project CH user; admin user is only used by ops scripts.
4. MySQL access goes through `pulse_user`; OpenFGA owns its own `openfga` database.
5. Schema diffs require updating BOTH `dev/` and `prod/` trees.
