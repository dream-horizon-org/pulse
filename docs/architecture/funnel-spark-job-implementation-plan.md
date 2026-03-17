# Funnel Spark Job — Implementation Plan

This document describes how to implement the Spark job that computes funnel results from S3 Parquet and writes to ClickHouse. It handles **multiple funnels per project** and **varying time ranges per funnel** (e.g. 7, 14, or 30 days).

---

## 1. Requirements Summary

| Requirement | Approach |
|-------------|----------|
| Multiple funnels per project | Group funnels by `project_id`; read S3 **once per project** for the max date range needed; compute all funnels for that project from the same DataFrame. |
| Varying time ranges per funnel | Each funnel has `date_range_days`. For a project, use **max(date_range_days)** across its funnels to build the S3 path; then filter events by each funnel’s own date range before computing. |
| On-save (single funnel) | Single funnel, single range: read S3 for that project and [date_from, date_to]; compute; write ClickHouse. |
| Daily (all funnels) | Load all funnels; group by project; for each project read S3 once (max range); for each funnel in that project filter by its range and compute; write all results to ClickHouse. |

---

## 2. Job Modes and Arguments

Single Spark/Glue job entrypoint with a **mode** and mode-specific arguments.

### 2.1 On-save mode

Triggered when a user saves a funnel. Computes one funnel.

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `mode` | string | yes | `on_save` |
| `funnel_id` | string | yes | Funnel to compute (e.g. UUID). |
| `project_id` | string | yes | Project (proj-xxx); used for S3 bucket and isolation. |
| `date_from` | string | yes | ISO date, e.g. `2026-03-09`. |
| `date_to` | string | yes | ISO date, e.g. `2026-03-15`. |

**Run date for output:** Use `date_to` as the report date (or “last day of range”) so that `otel.funnel_results.run_date` is consistent.

### 2.2 Daily mode

Triggered by cron. Computes all saved funnels.

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `mode` | string | yes | `daily` |
| `run_date` | string | yes | Report date (e.g. yesterday), ISO date. All funnels are computed for windows ending on or before this date. |

Funnel definitions (and thus per-funnel `date_range_days`) are loaded inside the job (API or MySQL).

---

## 3. Loading Funnel Definitions

- **On-save:** Fetch the single funnel by `funnel_id` (e.g. `GET /v1/funnel/saved?funnel_id=xxx` or read from MySQL if the job has DB access). You get: steps (event names + optional filters), `window_seconds`, `mode` (UNIQUE_USERS / SESSIONS), `date_range_days` (already reflected in `date_from`/`date_to` from the trigger).
- **Daily:** Fetch all saved funnels (e.g. `GET /v1/funnel/saved` or MySQL). You get a list of funnel definitions, each with `funnel_id`, `project_id`, `steps`, `window_seconds`, `mode`, `date_range_days`.

Use a small HTTP client or JDBC in the Spark driver to load definitions; then broadcast or pass the list to the rest of the job.

---

## 4. S3 Read Strategy (Multiple Funnels, Varying Ranges)

### 4.1 Path layout

- Bucket: `pulse-otel-{project_id}` (replace any `proj-` prefix or use as-is per your naming).
- Prefix: `vector-logs/{date}/{hour}/` with date as `YYYY-MM-DD`, hour as `HH`.
- Files: Parquet under that prefix (Vector writes here).

### 4.2 On-save

- Single funnel, single range.
- Build path list for `project_id` and [`date_from`, `date_to`]: e.g. for each date, `vector-logs/2026-03-09/*/`, `vector-logs/2026-03-10/*/`, … through `date_to`.
- Read once: `spark.read.parquet("s3://pulse-otel-{project_id}/vector-logs/2026-03-09/*/*.parquet", ...)` or list all date/hour prefixes and pass multiple paths. Filter to `timestamp` in [date_from, date_to + 1 day) if needed.
- Columns needed: `event_name`, `user_id`, `session_id`, `timestamp`, `project_id` (and any for filters). Match Athena/Vector schema (e.g. `timestamp` as timestamp).

### 4.3 Daily (multiple funnels per project, varying ranges)

1. **Group funnels by project:**  
   From the list of all funnels, group by `project_id`. Each group has one or more funnels, each with its own `date_range_days`.

2. **Per project, compute the max range:**  
   For project P, `max_days = max(f.date_range_days for f in funnels_of_P)`.  
   Window for P: `[run_date - max_days, run_date]` (inclusive or exclusive as you define).

3. **Read S3 once per project:**  
   For project P, build the set of prefixes for dates in `[run_date - max_days, run_date]` (e.g. all `vector-logs/YYYY-MM-DD/*/` in that range). Read all Parquet under those prefixes into one DataFrame `events_P`.

4. **Per-funnel date filter and compute:**  
   For each funnel F in project P:
   - Filter `events_P` to F’s range: e.g. `timestamp` in `[run_date - F.date_range_days, run_date]`.
   - Run funnel computation (sequential steps within `window_seconds`, identity = `user_id` or `session_id`).
   - Emit rows for `(funnel_id, run_date, step_index, step_name, user_count, conversion_pct)`.

5. **Write:**  
   Collect all rows from all funnels (all projects) and write to `otel.funnel_results` (append or overwrite per run_date; see below).

This way you never read S3 more than once per project per daily run, while still respecting each funnel’s own time range.

---

## 5. Funnel Computation Logic (PySpark)

Conceptually this mirrors ClickHouse’s `windowFunnel(window_seconds)(timestamp, cond1, cond2, ...)`.

- **Identity column:** `user_id` for UNIQUE_USERS, `session_id` for SESSIONS. If null/empty, skip or coalesce (e.g. to installation_id) per your product rules.
- **Steps:** Ordered list of event names (and optional filters). For each identity, find the maximum step index reached when events occur in order within a sliding window of `window_seconds` seconds.
- **Algorithm (one funnel):**
  1. Filter events to those in the funnel’s step set (and apply any step-level or global filters).
  2. For each identity, order events by `timestamp`.
  3. For each identity, compute “max step reached” in one pass: e.g. maintain current step index and last timestamp per step; advance when the next step’s event is within `window_seconds` of the previous step’s timestamp.
  4. Aggregate: for each step index (0 to N-1), count distinct identities that reached at least that step. From that you get `user_count` per step and can derive `conversion_pct` (e.g. step_i_count / step_0_count * 100).

Implementation options:

- **Window + UDAF:** Order by identity and timestamp; use a custom Pandas UDF or RDD mapPartitions to compute max step per identity, then aggregate.
- **Window + row-by-row in Scala/Python:** Same idea, express with window functions and then a grouped aggregation with custom logic.
- **Reference:** Replicate the semantics of [ClickHouse windowFunnel](https://clickhouse.com/docs/en/sql-reference/aggregate-functions/reference/windowfunnel) (first event matching cond1, then first event matching cond2 after cond1 within window_seconds, etc.).

Output schema per funnel: list of (step_index, step_name, user_count, conversion_pct). Map to ClickHouse rows: (funnel_id, project_id, run_date, step_index, step_name, user_count, conversion_pct).

---

## 6. Writing to ClickHouse

- **Table:** `otel.funnel_results` (see `backend/ingestion/clickhouse-funnel-results-schema.sql`).
- **Rows:** One per (funnel_id, run_date, step_index).

**Idempotency:**

- **On-save:** For the single funnel and the run_date used, delete existing rows for (funnel_id, run_date) then insert new rows (or use a replace/upsert if you have it).
- **Daily:** For the given `run_date`, delete all rows where `run_date = <run_date>` (so the daily run replaces the whole day’s results for all funnels), then insert the new batch. Alternatively, use a staging table and replace partition by run_date if your ClickHouse setup supports it.

Use the ClickHouse JDBC connector or the native client from the Spark driver/executors; batch inserts by partition or in chunks to avoid huge single inserts.

---

## 7. Implementation Phases

### Phase 1: Single-funnel (on-save) path

1. **Glue/EMR job:** One script (e.g. `funnel_job.py`) that:
   - Parses job arguments (mode, funnel_id, project_id, date_from, date_to).
   - Loads the funnel definition (HTTP or MySQL).
   - Builds S3 path(s) for the project and date range; reads Parquet into a DataFrame.
   - Filters to the funnel’s date range (for on-save it’s the full read).
   - Implements funnel logic (sequential steps, window_seconds, user_id or session_id).
   - Writes results to `otel.funnel_results` (delete then insert for that funnel_id + run_date).
2. **Test:** One project, one funnel, 7-day range; verify counts and conversion % match a known baseline or manual run.

### Phase 2: Multiple funnels, varying ranges (daily)

1. **Daily arguments:** Add `mode=daily` and `run_date`; when mode is daily, load all funnels (API or MySQL).
2. **Group by project:** Build a map project_id → list of funnel definitions.
3. **Per project:** For each project, compute max `date_range_days`, build S3 path list, read once; for each funnel in the project, filter events to that funnel’s range, run the same funnel logic, collect rows.
4. **Write:** For the given `run_date`, replace all existing rows for that run_date (or delete by run_date then insert), then insert the combined result set.
5. **Test:** Multiple projects, multiple funnels per project, mixed 7/14/30-day ranges; verify S3 is read once per project and results are correct per funnel.

### Phase 3: Hardening

1. **Error handling:** Per-funnel try/except; log failed funnel_id and continue with others; optionally write failed funnel_ids to a table or log for retry.
2. **Job status (on-save):** On success/failure, call pulse-server (webhook or API) to update `funnel_job.status` and `completed_at` / `error_message`, or have pulse-server poll Glue job status.
3. **Config:** Bucket prefix, ClickHouse URL, credentials (Glue connection or IAM), and API base URL for loading funnel definitions in config or environment.

---

## 8. Summary Table

| Topic | Decision |
|-------|----------|
| Multiple funnels per project | Group by project; one S3 read per project in daily mode. |
| Varying time ranges | Per project use max(date_range_days); per funnel filter events to its date_range_days before compute. |
| On-save | One funnel, one range; read S3 for that project + range; compute; write. |
| Daily | Load all funnels → by project → read S3 once per project (max range) → compute each funnel with its own range filter → write all for run_date. |
| Funnel logic | Sequential event matching within window_seconds; identity = user_id or session_id; output step-level counts and conversion %. |
| ClickHouse write | One row per (funnel_id, run_date, step_index); replace by run_date for daily; replace by (funnel_id, run_date) for on-save. |

This plan gives you a clear path to implement the Spark job with multiple funnels per project and varying time ranges while minimizing S3 reads and reusing one funnel-computation routine for both on-save and daily.
