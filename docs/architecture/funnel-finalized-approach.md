# Funnel — Finalized approach (index)

This page summarizes the **current** product and technical decision. Details live in the linked docs.

## Product

| Topic | Decision |
|-------|----------|
| **Saved funnels** | Users define steps + **predefined filters** (e.g. city, network provider, OS version) + **static conversion window** (`window_seconds`). |
| **Pre-computed metrics** | **Spark** (Glue/EMR) computes **all** saved funnels for **all** projects (daily batch + on-save for one funnel). |
| **Storage** | Results written to ClickHouse **`otel.funnel_results`**. Dashboard reads via pulse-server — no raw-event funnel scan at read time. |
| **Explore (on-the-fly)** | Ad-hoc funnel analysis runs **asynchronously** (202 + job id + poll); see `funnel-server-apis.md` §POST /v1/funnel/analyze. |

## Technical

- **Source data:** S3 Parquet (`pulse-otel-{project_id}/vector-logs/...`) — unchanged.
- **Spark:** Single-pass strategies per project where possible; column pruning; per-funnel `filters_json` and steps. See `funnel-spark-job-final-plan.md`.
- **ClickHouse DDL:** `backend/ingestion/clickhouse-funnel-results-schema.sql`
- **MySQL:** `funnel`, `funnel_job` — `V9__create_funnel_and_funnel_job_tables.sql`

## Doc map

| Document | Content |
|----------|---------|
| [Confluence — Data Architecture decision](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4775477367) | Options (A/B/C), **chosen Spark + CH approach**, **Finalized approach (production)** |
| [Confluence — Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590) | **MySQL** funnel/journey definitions; **ClickHouse** = `funnel_results` only (no `product_events`) |
| [Confluence — API](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289) | Funnel & journey REST API (aligned with `funnel-server-apis.md`) |
| [Confluence — Spark (job plan)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990) | Spark modes, S3 read strategy, write semantics — **no DDL** (`generate_funnel_spark_implementation_storage.py`) |
| `funnel-mysql-clickhouse-schema.md` | Repo pointer: MySQL definitions + ClickHouse `funnel_results` |
| `funnel-data-architecture.md` | §6 — finalized requirements + Spark → ClickHouse flow |
| `funnel-spark-implementation-plan.md` | Flows (save, daily, read, **async explore**), schemas |
| `funnel-spark-job-final-plan.md` | Spark job modes, read strategy, optimizations |
| `funnel-server-apis.md` | APIs including async analyze |
| `funnel-implementation-tasks.md` | Implementation checklist |
| `funnel-s3queue-streaming.md` | Archived alternatives only; **not** production |

## Confluence

- **Decision + finalized approach (single page):** [Funnel & Journey Data Architecture Decision Document](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4775477367) — section *Finalized approach (production)*.  
- **Deprecated:** [old finalized-only page](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4791042087) — redirect stub; do not edit.  
- **Schema:** [Funnel & User Journey Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590).  
- **API:** [Funnel & User Journey API](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289).
