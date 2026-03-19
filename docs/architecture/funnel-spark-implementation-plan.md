# Funnel Spark Implementation Plan

**Confluence:** [Funnel Spark implementation (job plan)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990) — Spark runtime only (no DDL). **DDL:** [Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590). Publish from `docs/confluence-drafts/generate_funnel_spark_implementation_storage.py`.

This document defines the **flow** and **schemas** for implementing the chosen funnel architecture: Spark jobs (on-save + daily) writing pre-computed results to ClickHouse, with the dashboard reading from ClickHouse via pulse-server.

**Finalized product rules (current):**

- **Predefined filters** — Saved funnels include fixed dimensions (e.g. city, network provider / carrier, OS version, device attributes) aligned with Parquet columns in `pulse-otel-{project_id}/vector-logs/`. Spark applies `filters_json` and step-level filters when computing.
- **Conversion window** — **Static** per funnel: `window_seconds` stored in MySQL and used by Spark for every run for that funnel (not overridden per dashboard read).
- **On-the-fly (explore) funnels** — Computed **asynchronously**: API returns **202 Accepted** + job identifier; client polls job status; results returned when the job completes (Spark or long-running ClickHouse query). Does not block on synchronous `windowFunnel` in the API for large data.

---

## 1. Flow Definition

### 1.1 High-Level Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FLOW 1: User saves funnel                                                                │
│   UI → POST /v1/funnel (save) → pulse-server                                             │
│   pulse-server: persist funnel definition (MySQL) → trigger Spark job (async)            │
│   Spark job: read S3 Parquet (date window) → compute funnel → write ClickHouse           │
│   User: polls GET /v1/funnel/{id}/results or GET /v1/funnel/{id}/job-status until ready   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FLOW 2: Daily batch (all funnels)                                                        │
│   Cron (e.g. 01:00 UTC) → trigger Spark job                                              │
│   Spark job: load all saved funnels (API or MySQL) → read S3 Parquet once                │
│            → compute all funnels in single pass → write ClickHouse otel.funnel_results   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FLOW 3: Dashboard reads funnel data                                                      │
│   UI → GET /v1/funnel/{id}/results (optional: date range)                                │
│   pulse-server: query ClickHouse otel.funnel_results → return step counts                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FLOW 4: On-the-fly funnel (explore, async)                                               │
│   UI → POST /v1/funnel/analyze (async) or dedicated async endpoint → 202 + job_id       │
│   pulse-server: enqueue Spark job OR submit async ClickHouse query                       │
│   User: polls GET …/job-status (or existing query job API) until SUCCEEDED               │
│   User: GET results (from job output store or temporary result table)                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Flow 1 — Save Funnel (Detailed)

| Step | Actor | Action |
|------|--------|--------|
| 1 | UI | `POST /v1/funnel` with body: name, steps, **predefined global filters** (city, network provider, OS version, etc.), **date_range_days**, **window_seconds** (static conversion window), mode (UNIQUE_USERS \| SESSIONS). |
| 2 | pulse-server | Validate request; generate `funnel_id` (e.g. UUID); persist row in `funnel` (MySQL); enqueue or invoke Spark job with `funnel_id`, `project_id`, `date_from`, `date_to`. |
| 3 | pulse-server | Return `202 Accepted` with `funnel_id` and `job_id` (if job tracking is used). |
| 4 | Spark job (on-save) | Load funnel definition by `funnel_id` (from API or MySQL); read S3 Parquet for `project_id` and date range; compute funnel; write to `otel.funnel_results`; optionally update job status. |
| 5 | UI | Poll `GET /v1/funnel/{id}/job-status` or `GET /v1/funnel/{id}/results` until data appears or job completes. |

### 1.3 Flow 2 — Daily Batch (Detailed)

| Step | Actor | Action |
|------|--------|--------|
| 1 | Cron / Scheduler | Invoke Spark job with no funnel_id (or with a “daily” mode flag). |
| 2 | Spark job (daily) | Call `GET /v1/funnel/saved` (or read from MySQL) to get all saved funnels; for each project, read S3 Parquet once for the configured date window (e.g. last 7 days); compute all funnels in a single pass; write all results to `otel.funnel_results` (per funnel_id, run_date, step). |

### 1.4 Flow 3 — Read Results (Detailed)

| Step | Actor | Action |
|------|--------|--------|
| 1 | UI | `GET /v1/funnel/{id}/results?date_from=…&date_to=…` (or default to funnel’s configured range). |
| 2 | pulse-server | Query ClickHouse `otel.funnel_results` for `funnel_id`, optional date filter; aggregate if needed; map to `FunnelResponse` (steps, totalEnteredUsers, conversionRate). |
| 3 | pulse-server | Return JSON. |

### 1.5 Job Trigger Options

- **On-save:** Invoke Glue/EMR job via AWS SDK (e.g. `StartJobRun`) with job arguments: `funnel_id`, `project_id`, `date_from`, `date_to`. No queue required if Glue is used; otherwise an SQS queue + Lambda/worker can trigger the job.
- **Daily:** Cron (e.g. in pulse-alerts-cron or EventBridge) triggers the same Spark job in “daily” mode (no funnel_id); job fetches all funnels and runs single-pass.

---

## 2. Schemas

**Final MySQL DDL:** `backend/server/src/main/resources/db/migration/V9__create_funnel_and_funnel_job_tables.sql`  
**Daily job status:** Not stored; use Glue/cron logs only.

### 2.1 MySQL — Funnel (saved definitions)

Stores saved funnel definitions. Used by pulse-server and by the Spark job (to load funnel steps and date range).

```sql
-- Funnel definition (saved by user)
CREATE TABLE funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id          VARCHAR(64)  NOT NULL UNIQUE COMMENT 'External ID e.g. UUID',
    project_id         VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name               VARCHAR(255) NOT NULL COMMENT 'Display name',
    steps_json         JSON         NOT NULL COMMENT 'Array of { eventName, dataType?, stepFilters? }',
    window_seconds     BIGINT       NOT NULL DEFAULT 86400 COMMENT 'Funnel window in seconds',
    mode               VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    date_range_days    INT          NOT NULL DEFAULT 7 COMMENT 'Default lookback days for computation',
    filters_json       JSON         NULL COMMENT 'Global filters',
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by         VARCHAR(255) NULL,
    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**

- `steps_json`: same shape as existing `FunnelRequest.steps` (list of `FunnelStep`: eventName, dataType, pulseType, stepFilters).
- `date_range_days`: used by Spark to decide how many days of S3 data to read (e.g. 7 or 30).
- `filters_json`: predefined global filters (city, network provider, OS version, etc.) when the user configures them; nullable in DB. Same shape as `FunnelRequest.filters` (`field`, `operator`, `value`). Spark maps `field` to Parquet column names and applies `.filter()` in the job.

### 2.2 MySQL — funnel_job (on-save job status)

Tracks on-save Spark job execution so the UI can show “Computing…” and poll until complete.

```sql
-- Optional: track on-save Spark job status
CREATE TABLE funnel_job (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id          VARCHAR(64)  NOT NULL COMMENT 'References funnel.funnel_id',
    job_id             VARCHAR(255) NULL COMMENT 'Glue/EMR job run id',
    status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    run_date           DATE         NULL COMMENT 'Date of data computed',
    error_message      TEXT         NULL,
    started_at         TIMESTAMP    NULL,
    completed_at       TIMESTAMP    NULL,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_funnel_job_funnel (funnel_id),
    INDEX idx_funnel_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Notes:**

- On save: insert row with `status = PENDING`, then trigger Spark; Spark or a webhook updates to `RUNNING` / `SUCCEEDED` / `FAILED`.
- UI can poll `GET /v1/funnel/{id}/job-status` and show “Computing…” until `SUCCEEDED` or `FAILED`.

### 2.3 ClickHouse — Funnel Results

Pre-computed step counts per funnel, per run date. Written by Spark; read by pulse-server for the dashboard.

```sql
CREATE TABLE otel.funnel_results (
    funnel_id          String       COMMENT 'Same as MySQL funnel.funnel_id',
    project_id         String       COMMENT 'Project ID',
    run_date           Date         COMMENT 'Date of the data window (e.g. report date)',
    step_index         UInt8        COMMENT '0-based step index',
    step_name          String       COMMENT 'Event name for this step',
    user_count         UInt64       COMMENT 'Unique users (or sessions) reaching this step',
    conversion_pct     Float64      COMMENT 'Conversion % from step 0 to this step',
    created_at         DateTime64(3) DEFAULT now64(3),
    CONSTRAINT chk_step_index CHECK step_index < 32
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(run_date)
ORDER BY (funnel_id, run_date, step_index);
```

**Notes:**

- One row per (funnel_id, run_date, step_index). Typically 5–10 steps per funnel per day.
- For “last 7 days” view, the API can `SELECT ... WHERE funnel_id = ? AND run_date IN (last 7 days)` and aggregate or return the latest run_date.
- `conversion_pct` can be computed in Spark or in ClickHouse; storing it avoids recomputation.

### 2.4 S3 Parquet (Input to Spark)

Schema of the event data in S3 (per Athena/existing pipeline). Spark must read the same columns.

| Column | Type | Description |
|--------|------|-------------|
| `event_name` | string | Event name (e.g. app_open, search, checkout) |
| `project_id` | string | Project identifier |
| `user_id` | string | User identifier (for UNIQUE_USERS mode) |
| `session_id` | string | Session identifier (for SESSIONS mode) |
| `timestamp` | timestamp | Event time |
| `installation_id` | string | Optional |
| (others) | — | As in `athena-otel-tables.sql`: screen_name, device_*, etc. |

**Path pattern:** `s3://pulse-otel-{project_id}/vector-logs/{date}/{hour}/*.parquet`  
**Date range:** From funnel’s `date_range_days` (e.g. last 7 days from “today” or from `run_date`).

### 2.5 Spark Job Input (Arguments)

**On-save job:**

| Argument | Type | Description |
|----------|------|-------------|
| `mode` | string | `on_save` |
| `funnel_id` | string | Funnel to compute |
| `project_id` | string | Project ID (for S3 bucket and isolation) |
| `date_from` | string | ISO date (e.g. 2026-03-01) |
| `date_to` | string | ISO date (e.g. 2026-03-07) |

**Daily job:**

| Argument | Type | Description |
|----------|------|-------------|
| `mode` | string | `daily` |
| `run_date` | string | Report date (e.g. yesterday); job computes all funnels for that window |

Funnel definitions (steps, window_seconds, mode) are loaded inside the job via HTTP call to pulse-server `GET /v1/funnel/saved` or by reading from MySQL (if the job has DB access).

### 2.6 API Request/Response Shapes (Reference)

**Save funnel (new):**

- **Request:** `POST /v1/funnel`  
  Body: `{ "name", "steps": [ { "eventName", "dataType?", "stepFilters?" } ], "windowSeconds", "mode", "dateRangeDays?", "filters?" }`  
  (project_id / tenant from context.)

- **Response:** `202 Accepted`  
  Body: `{ "funnelId", "jobId?", "message": "Funnel saved. Computation started." }`

**Get results (new):**

- **Request:** `GET /v1/funnel/{id}/results?date_from=2026-03-01&date_to=2026-03-07`  
  (Defaults: use funnel’s `date_range_days` from today.)

- **Response:** Same shape as existing `FunnelResponse`:  
  `{ "steps": [ { "stepName", "count", "conversionRate", "dropoffRate" } ], "totalEnteredUsers", "overallConversionRate" }`  
  Data source: ClickHouse `otel.funnel_results`.

**Get job status (optional):**

- **Request:** `GET /v1/funnel/{id}/job-status`  
- **Response:** `{ "status": "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED", "jobId?", "completedAt?", "errorMessage?" }`

**List saved funnels (for Spark daily job):**

- **Request:** `GET /v1/funnel/saved` (or per project: `GET /v1/funnel/saved?project_id=proj-xxx`)  
- **Response:** `{ "funnels": [ { "funnelId", "projectId", "name", "steps", "windowSeconds", "mode", "dateRangeDays" } ] }`

---

## 3. Summary Table

| Component | Schema / Contract | Purpose |
|-----------|-------------------|--------|
| **MySQL** | `funnel` | Store saved funnel definitions (name, steps, window, date range). |
| **MySQL** | `funnel_job` (optional) | Track on-save Spark job status for UI polling. |
| **ClickHouse** | `otel.funnel_results` | Store pre-computed step counts; dashboard reads from here. |
| **S3 Parquet** | event_name, user_id, session_id, timestamp, project_id, … | Input to Spark. |
| **Spark (on-save)** | Args: mode=on_save, funnel_id, project_id, date_from, date_to | Compute one funnel, write ClickHouse. |
| **Spark (daily)** | Args: mode=daily, run_date | Load all funnels, single pass, write ClickHouse. |
| **pulse-server** | POST /v1/funnel, GET /v1/funnel/{id}/results, GET /v1/funnel/{id}/job-status, GET /v1/funnel/saved | Persist funnel, trigger job, serve results and status. |

This completes the flow and schema definition for the funnel Spark implementation. Next steps would be: add MySQL migrations, implement REST endpoints, implement Spark job (PySpark), and wire Glue/EMR + cron.
