# Funnel Spark Job — Final Implementation Plan

Single Spark/Glue job for all funnels across all projects. Plan plus optimizations.

---

## 1. Job Scope and Modes

| Item | Decision |
|------|----------|
| **Scope** | One job run processes **all funnels across all projects** (daily) or one funnel (on-save). |
| **On-save** | Args: `mode=on_save`, `funnel_id`, `project_id`, `date_from`, `date_to`. Load one funnel, read S3 for that project + range, compute, write. Run date = `date_to`. |
| **Daily** | Args: `mode=daily`, `run_date`. Load all funnels (API or MySQL), group by `project_id`, process each project, write all results for `run_date`. |

---

## 2. Data Read Strategy

| Item | Decision |
|------|----------|
| **S3 reads** | **One read per project** (not per funnel). Daily: for each project read once with max date range; on-save: one read for that project’s range. |
| **Time range** | Per project use **max(date_range_days)** across its funnels. Build paths for `[run_date - max_days, run_date]`. Per funnel: filter the same DataFrame to **its own** `date_range_days` before compute. |
| **Path layout** | Bucket: `pulse-otel-{project_id}`. Prefix: `vector-logs/YYYY-MM-DD/HH/`. Parquet under that (Vector schema). |
| **Temporal** | **Full-window read** each run (no incremental-by-day merge). We read the full required window every time. |

---

## 3. Optimizations

### 3.1 Column pruning

- **Base columns (always):** `event_name`, `timestamp`, `user_id`, `session_id`, `project_id`.
- **Per project:** Before reading, compute **union of all columns** referenced by any funnel in that project (from `steps_json` / `filters_json` and step filters). Map API field names to Parquet names (e.g. `screen.name` → `screen_name`).
- **Read:** `spark.read.parquet(paths).select(*columns_for_project)`. If any funnel uses `props`, add `props` to the set.
- **Effect:** Only base + union of filter columns are read from S3; other columns are skipped.

### 3.2 Single read per project, per-funnel filter in Spark

- Read S3 once per project with the pruned column set above.
- For each funnel in that project: apply **that funnel’s** filter expression in Spark (`.filter(build_filter_expr(funnel))`), then run funnel logic. No extra S3 reads.

### 3.3 Distributed read and spill (memory)

- Spark reads **partition by partition**; the full project is not loaded “as a whole” into one machine.
- Shuffle (groupBy, window) **spills to disk** when executor memory is full. Rely on default `spark.shuffle.spill=true`.
- **No** `collect()` of full result on the driver. Write from executors (e.g. `foreachPartition` or distributed `write`) to ClickHouse.

### 3.4 Repartitioning for large projects

- After read, if a project’s DataFrame is large: **repartition** (e.g. by date or by hash of identity) so partition size is bounded and spill is effective. Example: `events.repartition(n)` or `repartition("date")`.

### 3.5 Very large projects (optional escalation)

- If one project still OOMs or is too slow: **(a)** increase executor memory and partitions, or **(b)** process that project in **time chunks** (e.g. week-by-week), compute funnel per chunk, then **aggregate** step counts (e.g. sum users per step across chunks or merge user-level state). Ensures the job never holds more than one chunk in the pipeline.

---

## 4. End-to-End Flow

### On-save

1. Parse args: `mode`, `funnel_id`, `project_id`, `date_from`, `date_to`.
2. Load funnel definition (API/MySQL).
3. Compute columns to read: base + columns referenced by this funnel’s filters (incl. `props` if needed).
4. Build S3 paths for `project_id` and `[date_from, date_to]`; read Parquet with `.select(*columns)`.
5. Apply funnel’s filters; run funnel logic (identity = user_id or session_id, window_seconds, ordered steps).
6. Write to ClickHouse for (funnel_id, run_date=date_to): delete existing rows for that funnel_id + run_date, then insert new rows.
7. Optionally call back to update `funnel_job.status`.

### Daily

1. Parse args: `mode`, `run_date`.
2. Load **all** saved funnels (API/MySQL); **group by** `project_id`.
3. For **each project**:
   - **max_days** = max(f.date_range_days for f in project’s funnels).
   - **columns** = base ∪ union of columns referenced by any funnel in this project.
   - Build S3 paths for `[run_date - max_days, run_date]`; **read once** with `.select(*columns)`.
   - Optionally **repartition** if data is large.
   - For **each funnel** in project:
     - Filter events to funnel’s range: `timestamp` in `[run_date - funnel.date_range_days, run_date]`.
     - Apply funnel’s filters; run funnel logic.
     - Collect result rows (funnel_id, project_id, run_date, step_index, step_name, user_count, conversion_pct).
4. **Write:** For `run_date`, delete all existing rows with `run_date = run_date`; insert all collected rows (all projects, all funnels) to ClickHouse from executors (no full collect on driver).

---

## 5. Funnel Logic (Summary)

- **Identity:** `user_id` (UNIQUE_USERS) or `session_id` (SESSIONS); coalesce nulls per product rules.
- **Steps:** Ordered event names (+ optional step filters). For each identity, max step index reached when events occur in order within a sliding window of `window_seconds`.
- **Output per funnel:** (step_index, step_name, user_count, conversion_pct). Map to ClickHouse row: (funnel_id, project_id, run_date, step_index, step_name, user_count, conversion_pct).

---

## 6. ClickHouse Write

- **Table:** `otel.funnel_results`.
- **On-save:** Replace rows for (funnel_id, run_date) then insert.
- **Daily:** Delete rows where run_date = run_date; insert full batch for that run_date.
- **Mechanism:** JDBC or native client; write from executors in batches (e.g. per partition or chunk), not one giant insert from driver.

---

## 7. Implementation Phases

| Phase | Scope |
|-------|--------|
| **1** | On-save only: one funnel, one range, column pruning, funnel logic, write. Test with one project/funnel. |
| **2** | Daily: all funnels, group by project, one read per project (max range + column union), per-funnel range + filter, write all for run_date. Test with multiple projects and varying ranges. |
| **3** | Hardening: per-funnel error handling, job status callback (on-save), config (S3, ClickHouse, API), repartition for large projects, optional time-chunking for very large projects. |

---

## 8. Optimization Checklist

| Optimization | How |
|--------------|-----|
| **Column pruning** | Base columns + union of filter columns per project; `.select(*cols)` when reading Parquet. |
| **Single read per project** | One S3 read per project; per-funnel filter and date filter in Spark. |
| **Varying time ranges** | Max(date_range_days) for S3 path; per-funnel filter to its date_range_days before compute. |
| **Single job for all projects** | One job run; loop over projects; no separate job per project. |
| **Memory / large data** | Partitioned read, spill enabled, no full collect; repartition if needed; optional time-chunking per project. |
| **Idempotent write** | On-save: replace by (funnel_id, run_date). Daily: replace by run_date. |
