# Funnel Spark Implementation — Final Task List

All tasks assume work from the **main** branch unless a sub-task specifies otherwise. Order reflects dependencies where applicable.

**Related docs:** Auth and API contracts → `funnel-server-apis.md`. Architecture and Spark choice → `funnel-data-architecture.md`. Spark job design → `funnel-spark-job-final-plan.md`.

---

## 1. Backend (pulse-server)

### 1.0 Auth and permissions (all funnel endpoints)

| ID  | Task                                                                                                                                                                                                                                                                 | Notes                                                                 |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| B0  | Enforce **Authorization** and **X-Project-ID** on all user-facing funnel endpoints; add **@RequiresPermission** per API: **can_edit** for POST (save), PUT (update), DELETE; **can_view** for GET (funnel, saved list, results, job-status). List saved: scope to project from header only; check **can_view** for that project ID. | See `funnel-server-apis.md`. Job-callback is internal (no project permission). Existing analyze/health/sessions should get **can_view**. |

### 1.1 Database and DAO


| ID  | Task                                                                                                               | Notes                                                          |
| --- | ------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------- |
| B1  | Run MySQL migration `V9__create_funnel_and_funnel_job_tables.sql` in target envs                                   | Migration file exists; ensure Flyway/liquibase runs it.        |
| B2  | Add `FunnelDao`: insert/update/getByFunnelId/getByProjectId/listAll, map to domain model                           | Use existing DAO patterns (Queries.java, MapStruct if needed). |
| B3  | Add `FunnelJobDao`: insert (PENDING), update status/jobId/runDate/error/startedAt/completedAt, getLatestByFunnelId | For on-save job status.                                        |
| B4  | Wire FunnelDao and FunnelJobDao in Guice module and ensure DB pool access                                          | e.g. add to existing module that provides MysqlClient.         |


### 1.2 Funnel save and Spark trigger


| ID  | Task                                                                                                                                                                                                                                             | Notes                                                                     |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| B5  | Add `POST /v1/funnel` (save): validate body (name, steps, **predefined filters** in `filters_json` — city, network provider, OS version, etc., **static** `windowSeconds`, mode, dateRangeDays), persist to `funnel`, create `funnel_job` row (PENDING), trigger Spark job with mode=on_save, funnel_id, project_id, date_from, date_to, pass filters + window to job args | Return 202 Accepted with funnelId and jobId. Spark writes to `otel.funnel_results`. |
| B6  | Add Spark job trigger client: call AWS Glue StartJobRun (or EMR equivalent) with job args; config for job name and region                                                                                                                        | Use AWS SDK; credentials via IAM or env.                                  |
| B7  | Generate date_from/date_to from funnel’s date_range_days (e.g. today - N to today) when triggering on-save job                                                                                                                                   | Use funnel’s timezone or UTC.                                             |


### 1.3 Funnel results and job status


| ID  | Task                                                                                                                                                                                                 | Notes                                                                    |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| B8  | Add ClickHouse read for funnel results: query `otel.funnel_results` by funnel_id (and optional date range), map to existing `FunnelResponse` shape (steps, totalEnteredUsers, overallConversionRate) | Reuse FunnelStepResult DTO; get project_id from TenantContext or funnel. |
| B9  | Add `GET /v1/funnel/{id}/results`: resolve funnel by id (and project), query ClickHouse via B8, return JSON                                                                                          | Optional query params: date_from, date_to.                               |
| B10 | Add `GET /v1/funnel/{id}/job-status`: return latest funnel_job row (status, jobId, completedAt, errorMessage) for UI polling                                                                         | 404 if funnel or job row missing.                                        |
| B11 | Add `GET /v1/funnel/saved`: list saved funnels **for the project only** (project from X-Project-ID). Check user’s **can_view** for that project ID; return only funnels for that project. Return funnelId, projectId, name, steps, windowSeconds, mode, dateRangeDays. | Used by UI list. Spark daily job may use internal mechanism to fetch all funnels by project. |


### 1.4 Job status callback (optional but recommended)


| ID  | Task                                                                                                                                                    | Notes                                                                               |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| B12 | Add `POST /v1/funnel/job-callback` (or internal-only): accept job_id/funnel_id, status (SUCCEEDED/FAILED), error_message, run_date; update `funnel_job` | Called by Spark job or Lambda when run completes. Secure with shared secret or IAM. |


---

## 2. ClickHouse


| ID  | Task                                                                                                                | Notes                              |
| --- | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| C1  | Apply `otel.funnel_results` DDL in target ClickHouse (run `backend/ingestion/clickhouse-funnel-results-schema.sql`) | Single-node or replicated per env. |


---

## 3. Spark job (Glue/EMR)

### 3.1 Repo and entrypoint


| ID  | Task                                                                                                                                                                                      | Notes                                |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| S1  | Create Spark job repo/package (e.g. under `deploy/glue/funnel-job/` or separate repo): Python entrypoint that parses job args (mode, funnel_id, project_id, date_from, date_to, run_date) | Single script or module.             |
| S2  | Implement funnel definition loader: for on_save fetch one funnel by funnel_id (HTTP GET or MySQL); for daily fetch all (GET /v1/funnel/saved or MySQL)                                    | Use requests or JDBC in driver.      |
| S3  | Implement column set for read: base (event_name, timestamp, user_id, session_id, project_id) + union of filter columns from funnel(s); map API field names to Parquet column names        | Support props if any funnel uses it. |


### 3.2 Funnel computation


| ID  | Task                                                                                                                                                                                                                            | Notes                                    |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| S4  | Implement windowFunnel-equivalent in PySpark: per identity (user_id or session_id), ordered events, max step reached within window_seconds; then aggregate to (step_index, user_count, conversion_pct)                          | Match ClickHouse windowFunnel semantics. |
| S5  | Apply per-funnel filters (from steps_json/**filters_json** — city, carrier, OS version, etc.) as Spark filter expressions; map API field names to Parquet columns                                                                                                                              | build_filter_expr(funnel).               |
| S6  | On-save path: single funnel, read S3 for project + [date_from, date_to] with column pruning, compute, write to ClickHouse (replace funnel_id + run_date)                                                                        | run_date = date_to.                      |
| S7  | Daily path: group funnels by project_id; per project max(date_range_days), read S3 once with column union; per funnel filter to its date range and compute; collect all result rows; write from executors (replace by run_date) | No full collect on driver.               |


### 3.3 Write and callback


| ID  | Task                                                                                                                                                                                                        | Notes                                         |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| S8  | Write results to ClickHouse via JDBC or native client from executors (foreachPartition or df.write.jdbc); implement delete-then-insert for idempotency (on-save: by funnel_id+run_date; daily: by run_date) | Batch inserts per partition.                  |
| S9  | On job success/failure call pulse-server job-callback (B12) to update funnel_job status                                                                                                                     | Optional; or rely on polling Glue job status. |


### 3.4 Infrastructure and schedule


| ID  | Task                                                                                                                                           | Notes                                      |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| S10 | Register Glue job (or EMR job): script path, DPU/worker config, IAM role for S3 + ClickHouse (and API if HTTP), job arguments from trigger     | Use Terraform or AWS console.              |
| S11 | Add daily schedule (EventBridge cron or pulse-alerts-cron): trigger Spark job with mode=daily, run_date=yesterday (or today) at e.g. 01:00 UTC | Ensure Glue job name and args are correct. |


---

## 4. UI (pulse-ui)

### 4.1 Save funnel


| ID  | Task                                                                                                                                                                                     | Notes                                                 |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| U1  | Add “Save funnel” action: modal or inline form with name, steps (existing builder), conversion window (windowSeconds), date range (dateRangeDays: 7/14/30), mode (UNIQUE_USERS/SESSIONS) | Reuse FunnelBuilder steps; add name + date range.     |
| U2  | Call `POST /v1/funnel` on save; on 202 show success and funnelId; optionally redirect to funnel detail or “Saved funnels” list                                                           | Handle validation errors (4xx).                       |
| U3  | After save, show “Computing…” state and start polling `GET /v1/funnel/{id}/job-status` until SUCCEEDED or FAILED; on FAILED show error_message                                           | Poll interval e.g. 5–10 s; timeout after e.g. 10 min. |


### 4.2 Saved funnels list and detail


| ID  | Task                                                                                                                                                                                                                 | Notes                                                    |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| U4  | Add “Saved funnels” list view: call `GET /v1/funnel/saved`, show name, steps summary, date range, last updated; link to funnel detail/results                                                                        | New route e.g. /funnels/saved or tab in Funnel Analysis. |
| U5  | Add funnel detail/results view: by funnel id, call `GET /v1/funnel/{id}/results` (and optional date_from/date_to); render FunnelVisualization + FunnelDataTable with returned steps/totalEnteredUsers/conversionRate | Reuse existing FunnelVisualization and table components. |
| U6  | Add empty state and loading state for results view; show “No data yet” if job still running (link to job-status) or no rows in ClickHouse                                                                            | Use job-status when available.                           |


### 4.3 Edit and delete


| ID  | Task                                                                                                                                                                                    | Notes                                    |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| U7  | Add edit funnel: load funnel by id (from saved list or API), prefill form; on submit call update API (if backend exposes PUT /v1/funnel/{id}); backend triggers on-save Spark job again | Backend may need PUT and re-trigger job. |
| U8  | Add delete funnel: confirm then call `DELETE /v1/funnel/{id}`; backend deletes from MySQL (and optionally ClickHouse rows for that funnel_id); redirect to saved list                   | Backend needs DELETE endpoint.           |


### 4.4 Integration with existing Funnel Analysis


| ID  | Task                                                                                                                                                                       | Notes                                     |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| U9  | **On-the-fly explore:** Change ad-hoc analyze to **async** where needed: POST returns **202** + job id; UI polls job status then fetches results (or keep sync for small queries with timeout). Add entry points to “Save” and “Saved funnels” list | Align with finalized product: explore funnels computed asynchronously. |
| U10 | Add API routes in Constants/API_ROUTES for: POST /v1/funnel (save), GET /v1/funnel/saved, GET /v1/funnel/:id/results, GET /v1/funnel/:id/job-status, DELETE /v1/funnel/:id | Match backend paths.                      |


---

## 5. Backend (additional for UI)


| ID  | Task                                                                                                                                                                      | Notes              |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| B13 | Add `PUT /v1/funnel/{id}`: update funnel row (name, steps_json, window_seconds, date_range_days, etc.); create new funnel_job row (PENDING) and trigger on-save Spark job | Used by UI edit.   |
| B14 | Add `DELETE /v1/funnel/{id}`: delete funnel (and cascade funnel_job); optionally delete rows from ClickHouse `funnel_results` where funnel_id = id                        | Used by UI delete. |
| B15 | Add `GET /v1/funnel/{id}`: return single funnel definition by id (for edit form); 404 if not found or project mismatch                                                    | Used by UI edit.   |


---

## 6. Testing and docs


| ID  | Task                                                                                                                           | Notes                              |
| --- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------- |
| T1  | Backend: unit tests for FunnelDao, FunnelJobDao, and service methods (save, get results, job status, list saved)               | JaCoCo 80% on changed files.       |
| T2  | Backend: integration test for save → trigger (mock Glue), and for GET results (mock or test ClickHouse)                        | Optional but recommended.          |
| T3  | Spark job: local or dev test for on-save (one funnel, small S3 path) and daily (two projects, two funnels, varying ranges)     | Use small Parquet subset.          |
| T4  | UI: tests for save flow, saved list, results view, and job-status polling                                                      | Jest/React Testing Library.        |
| T5  | Update runbooks or ops docs: how to run migration, apply ClickHouse schema, deploy Glue job, and what to do if daily job fails | Point to Confluence combined page. |


---

## 7. Summary by area


| Area                                | Task IDs   |
| ----------------------------------- | ---------- |
| Backend auth + permissions          | B0         |
| Backend DB/DAO                      | B1–B4      |
| Backend save + Spark trigger        | B5–B7      |
| Backend results + job status + list | B8–B11     |
| Backend callback + edit/delete/get  | B12–B15    |
| ClickHouse                          | C1       |
| Spark job (logic + write + infra)   | S1–S11   |
| UI save + polling                   | U1–U3    |
| UI list + detail + edit/delete      | U4–U8    |
| UI integration + API constants      | U9–U10   |
| Testing and docs                    | T1–T5    |


---

## 8. Suggested order of implementation

1. **B1, C1** — Schemas applied.
2. **B0** — Auth and permissions (can be done alongside endpoint implementation; ensure each new endpoint has @RequiresPermission and uses X-Project-ID).
3. **B2, B3, B4** — DAOs and wiring.
4. **B5, B6, B7** — Save endpoint and Spark trigger (minimal: trigger can log instead of Glue until S10).
5. **S1–S9** — Spark job (on-save first, then daily).
6. **B8, B9, B10, B11** — Results and job-status and saved list APIs.
7. **U1–U6, U9–U10** — UI save, list, results, job-status polling, API constants.
8. **S10, S11** — Glue job registration and daily schedule.
9. **B12, S9** — Callback and Spark callback call.
10. **B13, B14, B15, U7, U8** — Edit and delete (backend + UI).
11. **T1–T5** — Tests and docs.
