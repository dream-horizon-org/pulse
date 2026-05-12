# ClickHouse — otel.* tables

## Purpose

Telemetry store. OTel Collector writes traces / logs / metrics; Pulse-specific tables hold derived analytics. All data is partitioned daily and clustered by `(ProjectId, PulseType, …, Timestamp)`.

## Source

`backend/db/dev/clickhouse/01..16_otel.<table>.sql` (numbered, applied in lex order by `init-clickhouse.sh`). Prod mirror: `backend/db/prod/clickhouse/otel.<table>.sql` (no prefix).

## Inventory

| # | Table | Written by | Engine / partition |
|---|-------|-----------|--------------------|
| 01 | `otel_logs` | OTel Collector `clickhouse` exporter | MergeTree, daily, ORDER BY `(ProjectId, PulseType, EventName, Timestamp)` |
| 02 | `otel_traces` | OTel Collector | MergeTree, daily, ORDER BY `(ProjectId, PulseType, SpanName, Timestamp)` |
| 03 | `stack_trace_events` | `pulse-server` (after crash/ANR/non-fatal route via collector) | MergeTree |
| 04 | `funnel_results` | `analytics_jobs` (server / Spark) | MergeTree |
| 05 | `journey_results` | `analytics_jobs` | MergeTree |
| 06 | `root_cause_cache` | `pulse-server` RCA jobs | MergeTree |
| 07 | `screen_root_cause_cache` | `pulse-server` RCA jobs | MergeTree |
| 08 | `project_monthly_usage` | Vector `clickhouse_project_events` sink + MVs `project_monthly_logs_mv`, `project_monthly_traces_mv`, `project_monthly_stack_traces_events_mv`, plus the metric-table MVs (10–13) | SummingMergeTree-style aggregation table |
| 09 | `session_replay_events` (+ `session_replay_events_mv`) | OTel logs path | MergeTree |
| 10 | `otel_metrics_sum` (+ `project_monthly_metrics_sum_mv`) | OTel Collector | MergeTree |
| 11 | `otel_metrics_histogram` (+ MV) | OTel Collector | MergeTree |
| 12 | `otel_metrics_exp_histogram` (+ MV) | OTel Collector | MergeTree |
| 13 | `otel_metrics_summary` (+ MV) | OTel Collector | MergeTree |
| 14 | `interaction_heatmaps_daily` (+ `interaction_heatmaps_daily_mv`) | derived from `otel_logs` click events | MergeTree |
| 15 | `event_catalog_entries` | server populates from observed events | MergeTree |
| 16 | `session_summary` | derived from logs/traces | MergeTree |

> `otel_metrics_gauge` is created by the OTel Collector exporter itself (its built-in DDL); only the four other metric tables are pre-created here. The collector config explicitly sets `create_schema: false`, so the gauge table must exist before traffic — it is shipped as part of the upstream exporter's first-run path.

## Inputs

- `backend/ingestion/otel-collector.yaml` writes `otel_traces`, `otel_logs`, `otel_metrics_*`.
- `vector/vector.yaml` writes `project_monthly_usage` (alongside the MVs that also feed it from logs/traces/metrics/stack-traces).
- `pulse-server` writes `stack_trace_events`, `*_root_cause_cache`, `funnel_results`, `journey_results`, `event_catalog_entries`, `session_summary`.

## Outputs

- Dashboard queries (UI screens, alerts, RCA).
- Alerts cron evaluates against the same tables.
- AI agent (`pulse_ai/`) reads via tenant CH credentials.

## Operational notes

- All base tables `MergeTree`, partitioned `toYYYYMMDD(Timestamp)`. TTL is not enforced in the SQL — retention is a runtime decision (manual partition drops or future TTL DDL).
- Bloom-filter skip indexes on `TraceId`, `SessionId`, `UserId`, `AppInstallationId`, `SpanId`, `ScreenName` (logs). Traces has analogous indexes.

## Failure modes

- Writing without `create_schema: false` discipline would lead to drift between exporter-managed and Pulse-managed schemas — keep `create_schema: false`.
- Forgetting to add the corresponding MV when adding a new metric / log family → `project_monthly_usage` undercount.

## Related

- `clickhouse/materialized-columns.md` — full column source map.
- `clickhouse/row-policies.md` — per-project isolation.
- `clickhouse/migrations.md` — apply ordering and bootstrap notes.

## Open questions

- No retention policy in SQL; expected to be added as `TTL Timestamp + INTERVAL N DAY DELETE` per table.
