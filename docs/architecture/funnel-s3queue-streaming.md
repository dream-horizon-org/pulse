# S3Queue Streaming: S3 → ClickHouse for Funnel Queries

> **Production decision (current):** Funnel **definitions** live in **MySQL** ([Schema Design — Confluence](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590)). Spark reads **S3 Parquet** and writes **only** **`otel.funnel_results`** in ClickHouse. **Do not** create **`otel.product_events`** or **`otel.custom_events`** for this pipeline.
>
> This file is **archived reference** only (S3Queue / incremental-state alternatives).

This document describes what is needed to **stream raw custom-event data from S3 into ClickHouse** using the **S3Queue** table engine, then run **funnel queries** (e.g. `windowFunnel`) on that data in ClickHouse.

**Current flow (for context):** Vector writes custom events to S3 Parquet per project (`pulse-otel-{project_id}/vector-logs/YYYY-MM-DD/HH/`). The previous funnel plan used a Spark job to read S3, compute funnel results, and write to `otel.funnel_results`.

**New approach:** Use ClickHouse **S3Queue** to continuously ingest the same S3 Parquet into a ClickHouse table, then run funnel queries directly on that table (no Spark for funnel computation).

---

## 1. What S3Queue Does

- **S3Queue** is a ClickHouse table engine that **streams** files from an S3 path (with wildcards) into ClickHouse.
- It is **consumer-style**: once a file is successfully processed, it can be tracked so it is not re-read (state in ZooKeeper/ClickHouse Keeper).
- Typical pattern:
  1. **S3Queue table** — virtual table bound to an S3 path pattern (e.g. `s3://bucket/prefix/*`).
  2. **Materialized View** — `SELECT ... FROM s3queue_table` → `INSERT INTO` a **MergeTree** table. When the MV is attached, S3Queue starts background ingestion.
  3. **MergeTree table** — stores the ingested rows; funnel queries run on this table.

So you need: **S3Queue table** + **Materialized View** + **MergeTree target table** (and optionally **ZooKeeper/Keeper** for tracking).

---

## 2. Prerequisites

### 2.1 ClickHouse version

- **S3Queue** and **S3** engines support **Parquet** as the format parameter.
- **S3Queue** requires **mode** to be set (from 24.6+); recommended **24.7+** for current settings (e.g. `processing_threads_num` without `s3queue_` prefix).
- Confirm your ClickHouse version supports:
  - `ENGINE = S3Queue(path, [credentials], format, [compression])`
  - `format = 'Parquet'` (or `'Parquet'` as the format argument).

### 2.2 ZooKeeper or ClickHouse Keeper

- S3Queue uses **Keeper** (or ZooKeeper) to track which files have been processed (`keeper_path` setting).
- **Required** for:
  - **ordered** mode: store the lexicographically max processed file name.
  - **unordered** mode: store the set of processed file paths.
- Without Keeper, you cannot reliably avoid re-processing the same files across restarts.
- **Action:** Ensure ClickHouse is configured with Keeper (e.g. `<zookeeper>` or `<keeper>` in config) and that the cluster/single node can reach it.

### 2.3 S3 path and credentials

- **Path:** Same as today: **one bucket per project** — `pulse-otel-{project_id}`; prefix `vector-logs/` with date/hour layout `YYYY-MM-DD/HH/`.
- S3Queue path example for one project:
  - `https://pulse-otel-proj_xxx.s3.<region>.amazonaws.com/vector-logs/*`  
  - or `s3://pulse-otel-proj_xxx/vector-logs/*` (if your ClickHouse build supports this form).
- **Credentials:** AWS credentials must be available to ClickHouse (IAM role for EC2/ECS, or env vars, or explicit `aws_access_key_id` / `aws_secret_access_key` in the engine, or a named collection). Ensure the role/user has **s3:GetObject** and **s3:ListBucket** on the bucket.

---

## 3. Schema: Match Vector’s Parquet

Vector writes Parquet with **infer_schema: true**. The Athena DDL in `backend/ingestion/athena-otel-tables.sql` reflects the expected columns. The **MergeTree** table (and thus the columns you `SELECT` from the S3Queue table) must match the Parquet schema.

**Minimum for funnel queries:**

- `event_name` (String)
- `timestamp` (DateTime64 or DateTime)
- `user_id` (String)
- `session_id` (String)
- `project_id` (String)

**Recommended to ingest (for filters and compatibility with Athena):**

- All columns from the Athena DDL that Vector actually writes, e.g.:  
  `event_name`, `project_id`, `user_id`, `installation_id`, `session_id`, `timestamp`, `vector_observed_timestamp`, `os_name`, `os_version`, `app_build_id`, `app_build_name`, `device_manufacturer`, `device_model_identifier`, `service_name`, `screen_name`, `span_id`, `trace_id`, `scope_name`, `flags`, `props`, and any network/state fields you use.

**Note:** Parquet types map to ClickHouse types (e.g. STRING → String, TIMESTAMP → DateTime64). If Vector uses nested or map-like fields (e.g. `props`), align ClickHouse types (e.g. String or Map) with what Parquet contains.

---

## 4. Per-project vs single table

- **Current design:** One S3 bucket per project (`pulse-otel-{project_id}`). S3Queue is bound to **one path pattern per table**.
- **Options:**
  - **A. One S3Queue + one MergeTree per project**  
    When a project is created, run DDL to create:
    - `otel.s3queue_events_<project_id>` (S3Queue) → path `s3://pulse-otel-<project_id>/vector-logs/*`.
    - `otel.custom_events_<project_id>` (MergeTree) with the schema above.
    - Materialized View from S3Queue → `custom_events_<project_id>`.
    - Funnel queries run on `otel.custom_events_<project_id>` (and must be scoped by project in the API).
  - **B. Single MergeTree with `project_id`**  
    Still one S3Queue table per project (one path per bucket), but the MV inserts into **one shared** MergeTree table that includes a `project_id` column. Funnel queries filter by `project_id`. Eases schema evolution and reduces number of tables; requires one S3Queue + one MV per project, one shared MergeTree.

---

## 5. DDL outline (per project)

Example for **one project** (bucket `pulse-otel-proj_abc`, region `ap-south-1`), using a **shared** MergeTree table and Parquet.

**5.1 Shared MergeTree (create once)**

```sql
CREATE TABLE IF NOT EXISTS otel.custom_events (
    project_id     String,
    event_name     String,
    timestamp      DateTime64(3),
    user_id        String,
    session_id     String,
    installation_id String DEFAULT '',
    -- add other columns to match Parquet (os_name, screen_name, props, etc.)
    _ingested_at   DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
PARTITION BY (project_id, toYYYYMM(toDate(timestamp)))
ORDER BY (project_id, timestamp, user_id, session_id)
SETTINGS index_granularity = 8192;
```

**5.2 S3Queue table (one per project)**

Use the same column list as in the Parquet (or a subset). S3Queue uses the same parameters as the S3 engine; path can use wildcards.

```sql
-- Replace project_id_placeholder and region as needed.
CREATE TABLE IF NOT EXISTS otel.s3queue_events_proj_abc (
    event_name     String,
    project_id     String,
    user_id        String,
    session_id     String,
    timestamp      DateTime64(3),
    installation_id String,
    -- mirror other Parquet columns as needed
    _path          String  -- optional: S3 key for debugging
)
ENGINE = S3Queue(
    'https://pulse-otel-proj_abc.s3.ap-south-1.amazonaws.com/vector-logs/*',
    'Parquet',
    'zstd'  -- Vector uses parquet compression: zstd
)
SETTINGS
    mode = 'unordered',
    after_processing = 'keep',
    keeper_path = '/clickhouse/s3queue/proj_abc';
```

**5.3 Materialized View (one per project)**

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.mv_s3queue_to_custom_events_proj_abc
TO otel.custom_events
AS SELECT
    project_id,
    event_name,
    timestamp,
    user_id,
    session_id,
    installation_id,
    -- other columns
    now64(3) AS _ingested_at
FROM otel.s3queue_events_proj_abc;
```

Once the MV exists and is attached, S3Queue will start polling the S3 path and pushing rows into `otel.custom_events`. New files under `vector-logs/*` will be consumed according to `mode` and `keeper_path`.

---

## 6. S3Queue settings that matter

| Setting | Recommended | Notes |
|--------|-------------|--------|
| **mode** | `unordered` | Tracks each processed file in Keeper. Safer when files can appear out of order (e.g. late-arriving hours). |
| **after_processing** | `keep` | Do not delete or move files in S3 (Athena or other jobs may still use them). |
| **keeper_path** | Unique per project | e.g. `/clickhouse/s3queue/{project_id}` so state is per project. |
| **processing_threads_num** | e.g. 4–8 | Tune for throughput vs load. |
| **polling_min_timeout_ms** / **polling_max_timeout_ms** | Defaults or tune | How often to list S3 for new files. |

---

## 7. Funnel queries on the streamed table

- Once data is in `otel.custom_events` (or `otel.custom_events_{project_id}`), run **windowFunnel** (or the same logic you use today in `FunnelServiceImpl`) on this table.
- Example pattern (conceptually):

```sql
SELECT
    windowFunnel(86400)(toDateTime(timestamp), event_name = 'step1', event_name = 'step2', event_name = 'step3') AS level,
    count() AS users
FROM otel.custom_events
WHERE project_id = 'proj_abc'
  AND timestamp >= now() - INTERVAL 7 DAY
  AND timestamp < now()
GROUP BY level
ORDER BY level;
```

- Your existing funnel service can be extended to:
  - **Read from** `otel.custom_events` (filtered by `project_id` and time range) instead of (or in addition to) `otel_traces` / `otel_logs`.
  - Use the same **step definitions** (event names + optional filters) and **window** (e.g. 86400 seconds).
- For **saved funnels** and **daily reports**, you can either:
  - Run this query on demand and cache the result, or
  - Keep a separate pre-aggregated results table (e.g. `otel.funnel_results`) that you fill with a scheduled job that reads from `otel.custom_events` and runs the funnel aggregation (no Spark, just ClickHouse SQL or a cron that runs the query and writes to `funnel_results`).

---

## 8. Checklist: what you need for this to work

| # | Requirement | Status / action |
|---|-------------|------------------|
| 1 | ClickHouse 24.7+ (or version with S3Queue + Parquet) | Confirm and deploy. |
| 2 | ClickHouse Keeper (or ZooKeeper) configured and running | Required for S3Queue state. |
| 3 | S3 buckets per project: `pulse-otel-{project_id}` with prefix `vector-logs/` | Already in place (Vector). |
| 4 | AWS credentials for ClickHouse (IAM or keys) with s3:GetObject, s3:ListBucket | Configure in ClickHouse. |
| 5 | MergeTree table schema matching Vector Parquet (event_name, timestamp, user_id, session_id, project_id, etc.) | Create DDL; add columns as needed for filters. |
| 6 | One S3Queue table per project pointing at that project’s bucket/prefix | DDL or automation on project creation. |
| 7 | One Materialized View per project: S3Queue → MergeTree | DDL or automation. |
| 8 | Unique `keeper_path` per project | e.g. `/clickhouse/s3queue/{project_id}`. |
| 9 | Funnel query layer (pulse-server) updated to query the new MergeTree table with project_id + time range | Extend FunnelServiceImpl / API. |
| 10 | (Optional) Retention / TTL on `otel.custom_events` to control storage cost | e.g. TTL on partition or drop old partitions. |

---

## 9. Operational notes

- **New projects:** Run the S3Queue + MV DDL when a project is created (same place you create the S3 bucket or Athena table).
- **Backfill:** S3Queue will process **existing** files under the path as well as new ones. For a large backfill, consider tuning `processing_threads_num` and Keeper so that ingestion keeps up without overloading.
- **Monitoring:** Use `system.s3queue_log` and `system.s3queue_metadata_cache` (if available in your version) to see processed files and errors.
- **Athena:** Keeping S3 files unchanged (`after_processing = 'keep'`) means Athena and S3Queue both read the same data; no change to existing Athena setup.

This setup gives you **streaming** S3 → ClickHouse and **funnel queries** on the streamed table; you can then decide whether to keep pre-computed `funnel_results` (e.g. via a ClickHouse-native scheduled query) or only run funnel queries on demand against `otel.custom_events`.

---

## 10. Hybrid: S3Queue ingestion + scheduled pre-aggregation into funnel_results

To keep **ingestion** simple (S3Queue → `otel.custom_events`) and **funnel reads** fast, you can add a **scheduled job** that pre-aggregates funnel results into `otel.funnel_results`. The UI/API then reads only from `funnel_results` (same as the Spark-based design), without scanning raw events on every request.

### 10.1 Flow

```
S3 (Parquet) ──S3Queue+MV──► otel.custom_events (raw, all projects)
                                    │
                                    │  Scheduled job (e.g. daily + on-save)
                                    │  • Load saved funnels (MySQL / API)
                                    │  • For each funnel: windowFunnel on custom_events
                                    │  • INSERT into otel.funnel_results
                                    ▼
                             otel.funnel_results (pre-aggregated, per funnel_id + run_date)
                                    │
                                    │  GET /v1/funnel/{id}/results
                                    ▼
                             pulse-server → UI
```

- **S3Queue + MV:** Continuously stream S3 → `otel.custom_events`. No change.
- **Scheduled job:** Periodically (e.g. daily at 01:00 UTC) and/or on funnel save:
  - Load funnel definitions (from MySQL `funnel` table or `GET /v1/funnel/saved`).
  - For each funnel, run a ClickHouse query that computes `windowFunnel` over `otel.custom_events` for that funnel’s project, steps, window, and date range.
  - Write one row per step into `otel.funnel_results` (funnel_id, project_id, run_date, step_index, step_name, user_count, conversion_pct).
- **API:** `GET /v1/funnel/{id}/results` reads from `otel.funnel_results` (filter by funnel_id, optional date range). No heavy windowFunnel at request time.

### 10.2 What the scheduled job does (per funnel)

1. **Input:** Funnel definition from MySQL/API: `funnel_id`, `project_id`, `steps` (ordered event names + optional filters), `window_seconds`, `mode` (UNIQUE_USERS / SESSIONS), `date_range_days`.
2. **Date range:** e.g. `run_date = today() - 1` for daily; or last N days ending today for on-save. The job decides the range (e.g. last 7 days → run_date = today() - 1, data from [today-7, today-1]).
3. **ClickHouse query:** Run `windowFunnel` on `otel.custom_events` filtered by `project_id` and timestamp in range, with the funnel’s steps and window. Identity = `user_id` or `session_id` depending on `mode`. Same logic as `FunnelServiceImpl.buildFunnelQuery()` but against `otel.custom_events` and with step filters if present.
4. **Output:** Result has one row per “level” (step index) with count. Insert into `otel.funnel_results`: for each level, one row (funnel_id, project_id, run_date, step_index, step_name, user_count, conversion_pct). Replace existing rows for that (funnel_id, run_date) so the table stays idempotent (e.g. `ALTER TABLE ... DELETE WHERE funnel_id = ? AND run_date = ?` then INSERT, or use ReplacingMergeTree and rely on dedup).

### 10.3 Example aggregation query (conceptual)

For one funnel: project_id = `proj_abc`, steps = `['sign_up', 'first_bet']`, window_seconds = 86400, run_date = yesterday, mode = UNIQUE_USERS:

```sql
-- Step 1: Compute windowFunnel per user, then aggregate by level
WITH funnel_levels AS (
    SELECT
        user_id AS id,
        windowFunnel(86400)(toDateTime(timestamp), event_name = 'sign_up', event_name = 'first_bet') AS level
    FROM otel.custom_events
    WHERE project_id = 'proj_abc'
      AND timestamp >= toDateTime('2025-03-15 00:00:00')
      AND timestamp <  toDateTime('2025-03-16 00:00:00')
    GROUP BY user_id
)
SELECT level, count() AS user_count
FROM funnel_levels
GROUP BY level
ORDER BY level;
```

Then map `level` (0, 1, 2) to step_index and step_name, compute conversion_pct (user_count at step N / user_count at step 0 * 100), and INSERT into `otel.funnel_results` with funnel_id, project_id, run_date.

### 10.4 Where the job runs

| Option | Description |
|--------|-------------|
| **Cron + script** | A cron job (e.g. on a box or in CI) runs a script that: calls MySQL/API for saved funnels, then for each funnel runs the aggregation query (e.g. via `clickhouse-client` or HTTP) and inserts into `funnel_results`. |
| **pulse-alerts-cron** | Reuse the existing Java cron service: add a scheduled task that loads funnels, builds and runs ClickHouse queries via `ClickhouseQueryService`, and writes to `funnel_results`. Same DB/API as pulse-server. |
| **Dedicated worker** | A small service (e.g. Go/Python) that runs on a schedule and optionally subscribes to “funnel saved” events to run on-save aggregation. |
| **ClickHouse only** | ClickHouse has no built-in “run this query for each row in MySQL”; you’d need something external to drive which funnels to compute. So the **driver** of “which funnels and when” stays outside ClickHouse (cron, pulse-server, or worker). |

Recommended: **pulse-alerts-cron** or a **cron + script** that uses the same MySQL and ClickHouse as pulse-server, to avoid new infra.

### 10.5 On-save behaviour

- **Option A — Wait for schedule:** When the user saves a funnel, no immediate aggregation. First results appear after the next scheduled run (e.g. next day). Simple; no extra job trigger.
- **Option B — On-save trigger:** When the user saves a funnel (or updates it), pulse-server enqueues a one-off aggregation for that funnel (e.g. async task or call to the same cron/worker with “funnel_id=X”). The job runs windowFunnel on `custom_events` for that funnel and writes to `funnel_results`. User sees results within minutes. Requires a way to trigger the job (HTTP callback, queue, or cron that checks “pending” funnel_jobs).

Same `funnel_job` table can track “last run” or “pending” for that funnel so the UI can show “Computing…” until the aggregation has written to `funnel_results`.

### 10.6 Idempotency and overwrite

- **Daily job:** For each funnel, compute for `run_date = yesterday` (or a fixed window). Before inserting, delete existing rows for `(funnel_id, run_date)` in `otel.funnel_results`, then INSERT. Or use ReplacingMergeTree with (funnel_id, run_date, step_index) as the unique key and re-insert same keys so merges deduplicate.
- **On-save job:** Same idea: replace results for that funnel and the run_date(s) you just computed.

### 10.7 Summary

| Component | Role |
|-----------|------|
| **S3Queue + MV** | Stream S3 Parquet → `otel.custom_events`. |
| **Scheduled job** | Load saved funnels; for each, run windowFunnel on `custom_events`; write to `otel.funnel_results`. |
| **pulse-server** | `GET /v1/funnel/{id}/results` reads from `otel.funnel_results` only. |
| **UI** | Unchanged; shows pre-computed funnel results, optionally with “Computing…” until the job has run. |

No Spark; ingestion is S3Queue, aggregation is ClickHouse + a small scheduler (cron or pulse-alerts-cron).

---

## 11. Alternative: one materialized view per funnel (incremental windowFunnel state)

Instead of a scheduled job that runs full `windowFunnel` over `custom_events`, you can maintain **incremental funnel state** using `windowFunnelState()` and `windowFunnelMerge()` with **one materialized view per funnel**. New data flowing into `custom_events` automatically updates the state; the report is a fast merge over state blobs.

### 11.1 Idea

- **State:** Store per (funnel_id, user_id, date) an **AggregateFunction** state from `windowFunnelState(window_sec)(timestamp, cond1, cond2, ...)`. ClickHouse can **merge** two states for the same key (same user, same date) so that the result is as if all events had been in one stream.
- **Trigger:** A **materialized view** fires on **INSERT into `otel.custom_events`**. The MV sees only the **new block** of rows. For one specific funnel, it computes `windowFunnelState(...)` over that block (filtered by that funnel’s project and steps), grouped by user_id and date, and inserts into a **state table**.
- **State table:** One **AggregatingMergeTree** table for all funnels: key `(funnel_id, user_id, date)`, value = state. When the same (funnel_id, user_id, date) is inserted again (from a later block), background merge combines the states. So state is **incrementally updated** as new data arrives.
- **Report:** `SELECT level, count() FROM (SELECT user_id, windowFunnelMerge(state) AS level FROM state_table WHERE funnel_id = ? AND date IN (...) GROUP BY user_id) GROUP BY level`. No scan of raw events; only state table read + merge.

### 11.2 Flow

```
S3 → S3Queue+MV → otel.custom_events
                        │
                        │  ON INSERT (per block)
                        ├──► MV_funnel_1 → INSERT state (funnel_1, user_id, date, state)
                        ├──► MV_funnel_2 → INSERT state (funnel_2, user_id, date, state)
                        └──► ...
                        │
                        ▼
                  otel.funnel_states (AggregatingMergeTree)
                  (funnel_id, user_id, date, state)
                        │
                        │  Report: windowFunnelMerge(state) GROUP BY user_id, then GROUP BY level
                        ▼
                  GET /v1/funnel/{id}/results (query state table)
```

### 11.3 Schema

**State table (shared, one per cluster):**

```sql
-- One row per (funnel_id, user_id, date) with merged windowFunnel state.
-- Up to 32 steps: UInt8 x 32 in AggregateFunction (syntax depends on ClickHouse version).
CREATE TABLE IF NOT EXISTS otel.funnel_states
(
    funnel_id   String,
    user_id     String,
    date        Date,
    state       AggregateFunction(windowFunnel(86400), DateTime, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8, UInt8)
    -- Adjust: windowFunnel(86400) and one UInt8 per step (max 32). Or use a fixed max steps.
)
ENGINE = AggregatingMergeTree()
PARTITION BY (funnel_id, toYYYYMM(date))
ORDER BY (funnel_id, user_id, date)
SETTINGS index_granularity = 8192;
```

**Materialized view (one per funnel):**  
Runs when rows are inserted into `custom_events`. Filters by this funnel’s `project_id`, computes state for the **new block only**, and inserts into `otel.funnel_states` with this funnel’s `funnel_id`.

```sql
-- Example for funnel_id = 'funnel_abc', project_id = 'proj_xyz', 3 steps, window 86400.
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.mv_funnel_states_funnel_abc
TO otel.funnel_states
AS SELECT
    'funnel_abc' AS funnel_id,
    user_id,
    toDate(timestamp) AS date,
    windowFunnelState(86400)(
        toDateTime(timestamp),
        event_name = 'sign_up',
        event_name = 'first_bet',
        event_name = 'purchase'
    ) AS state
FROM otel.custom_events
WHERE project_id = 'proj_xyz'
GROUP BY user_id, date;
```

In practice you must fix the MV body to this funnel’s steps and filters (and window if it varies). So **creating a new funnel** implies **running DDL** to create a new MV (e.g. from pulse-server or a job after insert into MySQL `funnel`).

### 11.4 Report query (API / UI)

```sql
SELECT level, count() AS user_count
FROM (
    SELECT user_id, windowFunnelMerge(state) AS level
    FROM otel.funnel_states
    WHERE funnel_id = 'funnel_abc'
      AND date >= today() - 7
      AND date <= today()
    GROUP BY user_id
)
GROUP BY level
ORDER BY level;
```

Map `level` → step_index and step_name, compute conversion_pct, and return as `funnel_results`-shaped response (or write into `otel.funnel_results` for a consistent API).

### 11.5 Pros and cons

| Pros | Cons |
|------|------|
| **Incremental:** Only new blocks are processed; no full scan of `custom_events` for each report. | **One MV per funnel:** New funnel ⇒ new DDL (create MV). Requires automation (e.g. on funnel save). |
| **Fast reads:** Report is merge over state table (small). | **Step changes:** If funnel steps/window change, you must drop and recreate the MV and optionally backfill state. |
| **No separate cron** for aggregation; state is updated as data lands. | **State table size:** One (funnel_id, user_id, date) per funnel per user per day. Still much smaller than raw events. |
| **Same pattern** as Approach A in the main funnel doc (state + merge). | **Sessions vs users:** Example above is per user_id; for SESSIONS you’d use session_id and a separate state table or column. |

### 11.6 SESSIONS mode

For funnel by session instead of user, use a state table keyed by (funnel_id, session_id, date) and in the MV use `session_id` and the same `windowFunnelState(...)`. You can have one state table with an extra column `mode` (user/session) and different MVs writing to it, or separate tables.

### 11.7 Summary

- **One AggregatingMergeTree state table** (e.g. `otel.funnel_states`) keyed by (funnel_id, user_id, date) with a `windowFunnelState(...)` AggregateFunction.
- **One materialized view per funnel** on `otel.custom_events`: on INSERT, from the new block only, compute `windowFunnelState` for that funnel’s project and steps, GROUP BY user_id, date, insert into `funnel_states` with that funnel_id.
- **Report:** `windowFunnelMerge(state)` over the state table for that funnel and date range; then aggregate by level. Optionally materialize the result into `otel.funnel_results` so the rest of the API stays unchanged.
- **New funnel:** Run DDL to create the new MV (and optionally backfill state for the last N days from `custom_events` if you need history immediately).
