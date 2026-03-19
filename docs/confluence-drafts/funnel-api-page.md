# Funnel & User Journey API

**Canonical spec (keep in sync):** Repo file `docs/architecture/funnel-server-apis.md`.

**Architecture:** Saved **funnel definitions** live in **MySQL**. Pre-computed metrics for saved funnels are in ClickHouse **`otel.funnel_results`** (Spark). **Explore** (`/v1/funnel/analyze`) may use OTEL/Spark async paths — not the same as reading `funnel_results` unless explicitly cached.

**Supersedes:** Older drafts that used plural **`/v1/funnels`** — implementation uses singular **`/v1/funnel`**.

---

## Authentication (all user-facing funnel APIs except job-callback)

1. **`Authorization: Bearer <JWT>`** — required.
2. **`X-Project-ID: <projectId>`** — required; list and all operations are scoped to this project.
3. **OpenFGA:** **`can_view`** for reads; **`can_edit`** for create/update/delete.

**Job callback** (`POST /v1/funnel/job-callback`) is internal — shared secret / IAM, not FGA.

---

## Saved funnel APIs (new)

| Method | Path | Permission | Purpose |
|--------|------|------------|---------|
| POST | `/v1/funnel` | can_edit | Create funnel + trigger Spark on-save job. **202** `{ funnelId, jobId }`. |
| GET | `/v1/funnel/{funnelId}` | can_view | Single funnel definition from MySQL. |
| GET | `/v1/funnel/saved` | can_view | List funnels for `X-Project-ID` only. |
| PUT | `/v1/funnel/{funnelId}` | can_edit | Update + re-trigger Spark. **202** `{ funnelId, jobId }`. |
| DELETE | `/v1/funnel/{funnelId}` | can_edit | Delete funnel / jobs; optional CH cleanup. **204**. |
| GET | `/v1/funnel/{funnelId}/results` | can_view | Pre-computed rows from **`otel.funnel_results`** → same JSON shape as analyze (`FunnelResponse`). Query: `dateFrom`, `dateTo` optional. |
| GET | `/v1/funnel/{funnelId}/job-status` | can_view | Latest on-save job: `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED`. |
| POST | `/v1/funnel/job-callback` | internal | Spark completion → update `funnel_job`. |

### Save funnel request body (POST /v1/funnel)

- `name` (string, required)
- `steps` (array, required) — same as `FunnelRequest.steps` (`eventName`, `dataType`, `pulseType`, `stepFilters`)
- `windowSeconds`, `mode` (`UNIQUE_USERS` | `SESSIONS`), `dateRangeDays` (required)
- `filters` (optional) — global predefined filters; Spark applies on each run

---

## Existing explore / health / sessions (branch)

| Method | Path | Permission | Notes |
|--------|------|------------|-------|
| POST | `/v1/funnel/analyze` | can_view (recommended) | Ad-hoc funnel; **202** + poll for async; optional **200** small ranges. |
| POST | `/v1/funnel/health` | can_view (recommended) | Per-step crash/ANR/non-fatal. |
| POST | `/v1/funnel/sessions` | can_view (recommended) | Drop-off sessions for a step. |

---

## Journey APIs

Journey CRUD and analysis endpoints remain as in the prior product doc (paths under `/v1/journey` or equivalent) until merged into `funnel-server-apis.md`. Schema: [Funnel & User Journey Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590).

---

## Related

- [Schema Design — MySQL + ClickHouse funnel_results](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590)
- Repo: `docs/architecture/funnel-mysql-clickhouse-schema.md`, `backend/ingestion/clickhouse-funnel-results-schema.sql`
