# Funnel APIs — Pulse Server

Specification of funnel-related REST APIs in pulse-server: **new** saved-funnel APIs (to be implemented) and **existing** ad-hoc funnel endpoints (already in this branch).

**Confluence:** [Funnel & User Journey API](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289) — keep in sync with this file. **Funnel definitions** are persisted in **MySQL** per [Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590); pre-computed metrics are read from ClickHouse `otel.funnel_results`.

**Base path:** `/v1/funnel`

---

## Authentication and authorization

For all **user-facing** funnel APIs (except the internal job-callback):

1. **Both** of the following are required:
   - **`Authorization: Bearer <JWT>`** — Valid JWT for the user (or API-key auth where supported).
   - **`X-Project-ID: <projectId>`** — Project scope (e.g. `proj-xxx`). The server uses this to resolve project context and enforce permissions.

2. **Project permission** (OpenFGA) is checked against that project:
   - **`can_view`** — Required for **read** APIs: GET funnel, list saved funnels, get results, get job status. Only users/members with `can_view` on the project may call these.
   - **`can_edit`** — Required for **create/edit/delete** APIs: POST save funnel, PUT update funnel, DELETE funnel. Only users/members with `can_edit` on the project may create or modify funnels.

3. **List saved funnels** returns only funnels for the **project identified by `X-Project-ID`**. There is no cross-project listing for normal users. (Internal/Spark daily job may use a separate mechanism to fetch all funnels by project.)

The **job-callback** endpoint is internal (Spark/Lambda → server); it does not use project permissions and should be secured with a shared secret or IAM.

---

## New APIs (to be implemented)

### 1. Save funnel (create)

**Purpose:** Persist a funnel definition, create an on-save Spark job record, and trigger the Spark job. Used when the user saves a funnel from the UI.

**Permission:** `can_edit` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `POST` |
| **Path** | `/v1/funnel` |
| **Content-Type** | `application/json` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID (e.g. `proj-xxx`). Both auth header and project ID are checked. |
| `Content-Type` | Yes | `application/json` |

### Request body

```json
{
  "name": "Sign-up to first bet",
  "steps": [
    {
      "eventName": "sign_up_completed",
      "dataType": "TRACES",
      "pulseType": "EVENT",
      "stepFilters": [
        { "field": "platform", "operator": "EQ", "value": ["android"] }
      ]
    },
    {
      "eventName": "first_bet_placed",
      "dataType": "TRACES",
      "pulseType": "EVENT",
      "stepFilters": null
    }
  ],
  "windowSeconds": 86400,
  "mode": "UNIQUE_USERS",
  "dateRangeDays": 7,
  "filters": [
    { "field": "app.version", "operator": "IN", "value": ["1.2.0", "1.2.1"] }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Display name for the funnel. |
| `steps` | array | Yes | Ordered list of funnel steps (same shape as existing `FunnelRequest.steps`). |
| `windowSeconds` | number | Yes | Conversion window in seconds (e.g. 86400 = 24h). |
| `mode` | string | Yes | `UNIQUE_USERS` or `SESSIONS`. |
| `dateRangeDays` | number | Yes | Lookback days for Spark (e.g. 7, 14, 30). |
| `filters` | array | No | **Predefined global filters** for the saved funnel (city, network provider, OS version, etc.); same shape as `QueryRequest.filters`. Spark applies these on every pre-compute run. |

### Response

- **Status:** `202 Accepted`
- **Body:**

```json
{
  "funnelId": "550e8400-e29b-41d4-a716-446655440000",
  "jobId": "jr_abc123"
}
```

| Field | Description |
|-------|-------------|
| `funnelId` | External ID of the saved funnel (UUID). Used in subsequent GET/PUT/DELETE and for job-status/results. |
| `jobId` | Spark/Glue job run ID for polling (optional; may be empty if trigger is async). |

### Errors

- `400` — Validation failure (e.g. missing name, empty steps, invalid mode or dateRangeDays).
- `401` — Unauthorized (invalid or missing auth).
- `403` — Forbidden (no access to project).
- `404` — Project not found.

---

### 2. Get funnel (single)

**Purpose:** Return a single saved funnel definition by ID. Used by the UI for the edit form and by the Spark job (on-save path) to load one funnel.

**Permission:** `can_view` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `GET` |
| **Path** | `/v1/funnel/{funnelId}` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. Both auth header and project ID are checked. |

### Path parameters

| Parameter | Type | Description |
|------------|------|-------------|
| `funnelId` | string | External funnel ID (UUID). |

### Response

- **Status:** `200 OK`
- **Body:**

```json
{
  "funnelId": "550e8400-e29b-41d4-a716-446655440000",
  "projectId": "proj-xxx",
  "name": "Sign-up to first bet",
  "steps": [
    {
      "eventName": "sign_up_completed",
      "dataType": "TRACES",
      "pulseType": "EVENT",
      "stepFilters": [
        { "field": "platform", "operator": "EQ", "value": ["android"] }
      ]
    },
    {
      "eventName": "first_bet_placed",
      "dataType": "TRACES",
      "pulseType": "EVENT",
      "stepFilters": null
    }
  ],
  "windowSeconds": 86400,
  "mode": "UNIQUE_USERS",
  "dateRangeDays": 7,
  "filters": [
    { "field": "app.version", "operator": "IN", "value": ["1.2.0", "1.2.1"] }
  ],
  "createdAt": "2025-03-15T10:00:00Z",
  "updatedAt": "2025-03-15T10:00:00Z"
}
```

### Errors

- `401` — Unauthorized.
- `403` — Forbidden (funnel belongs to another project).
- `404` — Funnel not found.

---

### 3. List saved funnels

**Purpose:** List saved funnels for the project. Returns **only** funnels belonging to the project identified by `X-Project-ID`. Used by the UI “Saved funnels” list.

**Permission:** The server checks the user’s **`can_view`** permission for the **project ID passed in the `X-Project-ID` header**. Only if the user has `can_view` on that project is the list returned; otherwise 403 Forbidden.

| Attribute | Value |
|-----------|--------|
| **Method** | `GET` |
| **Path** | `/v1/funnel/saved` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. List is scoped to this project only. The server checks `can_view` for this project ID. |

### Query parameters

None. The project is taken from `X-Project-ID`; no cross-project listing.

### Response

- **Status:** `200 OK`
- **Body:**

```json
{
  "funnels": [
    {
      "funnelId": "550e8400-e29b-41d4-a716-446655440000",
      "projectId": "proj-xxx",
      "name": "Sign-up to first bet",
      "steps": [ ... ],
      "windowSeconds": 86400,
      "mode": "UNIQUE_USERS",
      "dateRangeDays": 7,
      "filters": [ ... ],
      "updatedAt": "2025-03-15T10:00:00Z"
    }
  ]
}
```

`steps` and `filters` use the same shapes as in the save request.

### Errors

- `401` — Unauthorized.
- `403` — Forbidden.

---

### 4. Update funnel

**Purpose:** Update an existing funnel definition and re-trigger the on-save Spark job. Used when the user edits a saved funnel and saves.

**Permission:** `can_edit` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `PUT` |
| **Path** | `/v1/funnel/{funnelId}` |
| **Content-Type** | `application/json` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. Both auth header and project ID are checked. |
| `Content-Type` | Yes | `application/json` |

### Path parameters

| Parameter | Type | Description |
|------------|------|-------------|
| `funnelId` | string | External funnel ID. |

### Request body

Same shape as **Save funnel** (name, steps, windowSeconds, mode, dateRangeDays, filters). All fields optional; only provided fields are updated.

### Response

- **Status:** `202 Accepted`
- **Body:** Same as Save funnel — `{ "funnelId": "...", "jobId": "..." }` (new job run for the updated funnel).

### Errors

- `400` — Validation failure.
- `401` — Unauthorized.
- `403` — Forbidden (funnel belongs to another project).
- `404` — Funnel not found.

---

### 5. Delete funnel

**Purpose:** Delete a saved funnel and its job records. Optionally delete corresponding rows from ClickHouse `funnel_results`. Used when the user deletes a funnel from the UI.

**Permission:** `can_edit` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `DELETE` |
| **Path** | `/v1/funnel/{funnelId}` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. Both auth header and project ID are checked. |

### Path parameters

| Parameter | Type | Description |
|------------|------|-------------|
| `funnelId` | string | External funnel ID. |

### Response

- **Status:** `204 No Content` (no body).

### Errors

- `401` — Unauthorized.
- `403` — Forbidden (funnel belongs to another project).
- `404` — Funnel not found.

---

### 6. Get funnel results

**Purpose:** Return pre-computed funnel results from ClickHouse (`otel.funnel_results`) for a saved funnel. Used by the UI to display the funnel visualization and conversion metrics after the Spark job has completed.

**Permission:** `can_view` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `GET` |
| **Path** | `/v1/funnel/{funnelId}/results` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. Both auth header and project ID are checked. |

### Path parameters

| Parameter | Type | Description |
|------------|------|-------------|
| `funnelId` | string | External funnel ID. |

### Query parameters

| Parameter | Type | Required | Description |
|------------|------|----------|-------------|
| `dateFrom` | string (date) | No | Start of date range (e.g. `2025-03-01`). Default: latest run_date or last N days. |
| `dateTo` | string (date) | No | End of date range (e.g. `2025-03-15`). |

### Response

- **Status:** `200 OK`
- **Body:** Same shape as existing **ad-hoc** `POST /v1/funnel/analyze` response (for compatibility with existing `FunnelVisualization`):

```json
{
  "steps": [
    {
      "stepName": "sign_up_completed",
      "count": 10000,
      "conversionRate": 100.0,
      "dropoffRate": 0.0
    },
    {
      "stepName": "first_bet_placed",
      "count": 2500,
      "conversionRate": 25.0,
      "dropoffRate": 75.0
    }
  ],
  "totalEnteredUsers": 10000,
  "overallConversionRate": 25.0,
  "groupedResults": null
}
```

| Field | Description |
|-------|-------------|
| `steps` | One entry per funnel step: step name, count at that step, conversion rate (%), dropoff rate (%). |
| `totalEnteredUsers` | Count of users/sessions that entered the funnel (step 1). |
| `overallConversionRate` | Percentage that completed the last step. |
| `groupedResults` | Optional; for future group-by support. |

### Errors

- `401` — Unauthorized.
- `403` — Forbidden (funnel belongs to another project).
- `404` — Funnel not found (or no results yet for that funnel).

---

### 7. Get funnel job status

**Purpose:** Return the latest on-save Spark job status for a funnel. Used by the UI to poll until the job completes (show “Computing…” then either results or error).

**Permission:** `can_view` on the project.

| Attribute | Value |
|-----------|--------|
| **Method** | `GET` |
| **Path** | `/v1/funnel/{funnelId}/job-status` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | `Bearer <JWT>`. |
| `X-Project-ID` | Yes | Project ID. Both auth header and project ID are checked. |

### Path parameters

| Parameter | Type | Description |
|------------|------|-------------|
| `funnelId` | string | External funnel ID. |

### Response

- **Status:** `200 OK`
- **Body:**

```json
{
  "funnelId": "550e8400-e29b-41d4-a716-446655440000",
  "jobId": "jr_abc123",
  "status": "SUCCEEDED",
  "runDate": "2025-03-15",
  "startedAt": "2025-03-15T10:00:05Z",
  "completedAt": "2025-03-15T10:02:30Z",
  "errorMessage": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `funnelId` | string | Funnel external ID. |
| `jobId` | string | Spark/Glue job run ID (may be null if not yet started). |
| `status` | string | `PENDING` \| `RUNNING` \| `SUCCEEDED` \| `FAILED`. |
| `runDate` | string (date) | Date of data computed (e.g. last day in range). |
| `startedAt` | string (ISO-8601) | When the job started. |
| `completedAt` | string (ISO-8601) | When the job finished (null if pending/running). |
| `errorMessage` | string \| null | Error details when `status` is `FAILED`. |

### Errors

- `401` — Unauthorized.
- `403` — Forbidden (funnel belongs to another project).
- `404` — Funnel or funnel_job row not found.

---

### 8. Job callback (internal)

**Purpose:** Called by the Spark job (or a Lambda) when an on-save or daily run completes. Updates `funnel_job` status so the UI polling returns SUCCEEDED/FAILED. **Not** called by the browser; secure with shared secret or IAM.

**Permission:** None (project permissions do not apply). Secured by internal auth (e.g. shared secret or IAM).

| Attribute | Value |
|-----------|--------|
| **Method** | `POST` |
| **Path** | `/v1/funnel/job-callback` |
| **Content-Type** | `application/json` |

### Request headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Depends | Bearer token or internal secret (e.g. `Bearer <internal-service-token>` or `X-Callback-Secret`). |
| `Content-Type` | Yes | `application/json` |

### Request body

```json
{
  "funnelId": "550e8400-e29b-41d4-a716-446655440000",
  "jobId": "jr_abc123",
  "status": "SUCCEEDED",
  "runDate": "2025-03-15",
  "errorMessage": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `funnelId` | string | Yes | External funnel ID. |
| `jobId` | string | Yes | Spark/Glue job run ID. |
| `status` | string | Yes | `SUCCEEDED` or `FAILED`. |
| `runDate` | string (date) | No | Date of data computed. |
| `errorMessage` | string | No | Error details when status is `FAILED`. |

### Response

- **Status:** `200 OK` or `204 No Content` (no body required).
- **Errors:** `400` (validation), `401` (invalid secret), `404` (funnel_job not found).

---

## Summary table (new APIs)

| # | Method | Path | Permission | Purpose |
|---|--------|------|------------|---------|
| 1 | POST | `/v1/funnel` | `can_edit` | Save funnel (create), trigger Spark job. |
| 2 | GET | `/v1/funnel/{funnelId}` | `can_view` | Get single funnel definition. |
| 3 | GET | `/v1/funnel/saved` | `can_view` | List saved funnels for the project (X-Project-ID only). |
| 4 | PUT | `/v1/funnel/{funnelId}` | `can_edit` | Update funnel and re-trigger Spark job. |
| 5 | DELETE | `/v1/funnel/{funnelId}` | `can_edit` | Delete funnel (and optional ClickHouse cleanup). |
| 6 | GET | `/v1/funnel/{funnelId}/results` | `can_view` | Get pre-computed results from ClickHouse. |
| 7 | GET | `/v1/funnel/{funnelId}/job-status` | `can_view` | Get latest on-save job status (for UI polling). |
| 8 | POST | `/v1/funnel/job-callback` | (internal) | Spark job completion callback. |

All user-facing APIs above require **both** `Authorization: Bearer <JWT>` and `X-Project-ID`; permission is then checked via OpenFGA for that project.

---

## Existing funnel APIs (already in this branch)

These endpoints are implemented in `FunnelController` on this branch. They are **not** part of the “new” saved-funnel API set above. They should also enforce **both** `Authorization` and `X-Project-ID`, and require **`can_view`** on the project (read-only ad-hoc analysis).

### POST /v1/funnel/analyze

**Purpose:** **On-the-fly (explore) funnel** — user-defined steps, time range, and filters without saving. Per **finalized product**, heavy exploration runs **asynchronously** so the API does not block on large scans.

| Attribute | Value |
|-----------|--------|
| **Method** | `POST` |
| **Path** | `/v1/funnel/analyze` |
| **Permission** | `can_view` (recommended when adding @RequiresPermission). |
| **Headers** | `Authorization: Bearer <JWT>`, `X-Project-ID: <projectId>`, `Content-Type: application/json` |

**Request body:** Same shape as `FunnelRequest`: `steps`, `timeRange` (start, end), `filters` (optional — city, network provider, OS version, etc.), `groupBy` (optional), `mode` (UNIQUE_USERS \| SESSIONS), `windowSeconds` (conversion window for this explore request).

**Response (finalized async behavior):**

- **`202 Accepted`** — Body includes `jobId` (and optionally `analyzeJobId`). Client polls **`GET /v1/funnel/analyze/{jobId}/status`** (or reuses existing ClickHouse/async query job status endpoint) until `SUCCEEDED` / `FAILED`.
- **`200 OK`** — Optional for **small** time ranges / fast paths: returns `FunnelResponse` directly (same as today). Product may standardize on 202-only for consistency.

**Result fetch:** After job completes, **`GET /v1/funnel/analyze/{jobId}/results`** (or equivalent) returns `FunnelResponse`. Implementation may use Spark for explore jobs or long-running ClickHouse query jobs; results are **not** read from `otel.funnel_results` unless explicitly materialized there for caching.

---

### POST /v1/funnel/health

**Purpose:** Funnel health: returns per-step crash/ANR/non-fatal counts for the given funnel and time range (used for health/quality view).

| Attribute | Value |
|-----------|--------|
| **Method** | `POST` |
| **Path** | `/v1/funnel/health` |
| **Permission** | `can_view` (recommended when adding @RequiresPermission). |
| **Headers** | `Authorization: Bearer <JWT>`, `X-Project-ID: <projectId>`, `Content-Type: application/json` |

**Request body:** Same as analyze — `FunnelRequest` (steps, timeRange, filters, mode, windowSeconds).

**Response:** `200 OK` — `FunnelHealthResponse`: `steps` (list of FunnelStepHealth), `totalCrashUsers`, `totalAnrUsers`, `totalNonFatalUsers`.

---

### POST /v1/funnel/sessions

**Purpose:** Fetch session-level details for users who dropped off at a specific funnel step (e.g. for debugging or drill-down).

| Attribute | Value |
|-----------|--------|
| **Method** | `POST` |
| **Path** | `/v1/funnel/sessions` |
| **Permission** | `can_view` (recommended when adding @RequiresPermission). |
| **Headers** | `Authorization: Bearer <JWT>`, `X-Project-ID: <projectId>`, `Content-Type: application/json` |

**Request body:** `FunnelSessionsRequest`: `steps`, `timeRange`, `filters`, `mode`, `windowSeconds`, `stepLevel` (1-based step index), `issueType` (e.g. ALL, CRASH, ANR), `limit` (default 100).

**Response:** `200 OK` — `FunnelSessionsResponse`: list of session details (e.g. session id, user id, step reached, issue type) for the requested step level.

---

**Note:** The existing three endpoints currently do not use `@RequiresPermission` in the codebase. When aligning with the rest of the API, they should require **both** `Authorization` + `X-Project-ID` and **`can_view`** on the project (and be annotated accordingly so `AuthorizationFilter` enforces it).
