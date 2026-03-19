# Funnel & journey schema — MySQL + ClickHouse

This document aligns the **repo** with the canonical **Confluence** design for funnel definitions and clarifies **ClickHouse** storage for the Spark pre-compute approach.

## Authoritative sources

| Topic | Source |
|-------|--------|
| **Funnel & journey MySQL schema** (full model: `description`, `ordered`, `closed`, `tags`, journey table, JSON shapes) | [Funnel & User Journey Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590) (Confluence) |
| **Funnel REST API** | [Funnel & User Journey API](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289) (Confluence) — must match `docs/architecture/funnel-server-apis.md` |
| **Implemented MySQL migration (minimal funnel + job)** | `backend/server/src/main/resources/db/migration/V9__create_funnel_and_funnel_job_tables.sql` — extend in future migrations to match Confluence `funnel` / `journey` tables as product ships |

## MySQL — funnel definitions

**Saved funnel definitions** live in **MySQL** (not ClickHouse). Spark loads definitions from MySQL or pulse-server API when running daily / on-save jobs.

- **Confluence** defines the full `funnel` row shape (`steps` JSON, `filters` JSON, `window_seconds`, `mode`, `ordered`, `closed`, `date_range_days`, `tags`, audit fields, etc.).
- **Repo V9** currently provides `funnel` + `funnel_job` with `steps_json`, `filters_json`, `window_seconds`, `mode`, `date_range_days` — align naming and columns with Confluence via follow-up migrations.

## ClickHouse — pre-computed funnel results only

**There is no `otel.product_events` table** and **no ClickHouse mirror of raw product/custom events** for the funnel pipeline. Raw events remain in **S3** (Vector Parquet). **Spark** reads S3, computes funnels using MySQL definitions, and writes **only** aggregated rows to ClickHouse.

### Table: `otel.funnel_results`

| Column | Type | Description |
|--------|------|-------------|
| `funnel_id` | String | Matches MySQL / Confluence `funnel_id` |
| `project_id` | String | Project scope |
| `run_date` | Date | Report / data window date |
| `step_index` | UInt8 | 0-based step index |
| `step_name` | String | Event name for the step |
| `user_count` | UInt64 | Users or sessions at this step |
| `conversion_pct` | Float64 | Conversion % from step 0 |
| `created_at` | DateTime64(3) | Insert time |

- **Engine:** `MergeTree`
- **Partition:** `toYYYYMM(run_date)`
- **Order:** `(funnel_id, run_date, step_index)`

Canonical DDL: **`backend/ingestion/clickhouse-funnel-results-schema.sql`**

### What was removed from design

- **`otel.product_events`** (or any ClickHouse “product events” / raw event fact table for funnel input) — **removed**. Do not create for this architecture.
- Optional **S3Queue → `custom_events`** path is **not** the production funnel input; see `funnel-s3queue-streaming.md` (archived reference only).

## Data flow (summary)

```
MySQL funnel (+ journey) definitions
        │
        ▼
Spark (daily + on-save) ──reads──► S3 Parquet (pulse-otel-{project_id}/vector-logs/...)
        │
        ▼
ClickHouse otel.funnel_results  ← only pre-computed aggregates
        │
        ▼
pulse-server GET /v1/funnel/{id}/results
```
