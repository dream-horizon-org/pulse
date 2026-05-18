# pulse-mcp — failing tools (why)

> **Note (2026):** MCP **query builder tools** (`get_query_tables`, `get_query_history`, `get_query_stats`, `get_universal_query_tables`, `get_universal_query_columns`) are **unregistered** — the feature was removed from the product. Rows below that mention those tools are **historical** (pre-removal debugging).

This document explains **why** specific MCP tools returned HTTP errors when exercised through Cursor’s **`user-pulse` MCP** against project **`fancode`** (see status tables in [`mcp-tools.md`](./mcp-tools.md)).

**Method**

1. **Live MCP** — observed status codes from integrated MCP (2026-05-14).
2. **Repo trace** — compared `pulse-mcp/src/tools/*.ts` paths and headers to **`backend/server`** JAX-RS resources and service guards.
3. **Subagent** — one read-only codebase exploration pass corroborated route mismatches and required headers/query params (same conclusions as manual grep).

**Scope caveat:** Deployed gateways (Kong, path prefixes, extra services) can differ from this monorepo snapshot. If production routes `/v3/metric/*` to another service, only this repo’s **pulse-server** wiring is analyzed below.

---

## Quick reference

| Tool(s) | Observed (fancode) | Primary cause (this repo) |
|--------|---------------------|---------------------------|
| `get_apdex_score`, `get_error_rate`, `get_interaction_time`, `get_interaction_categorization` | **404** | No pulse-server JAX-RS for `POST /v3/metric/*`; UI constants still reference those paths |
| `list_session_replays` | **404** | MCP uses `GET /v1/session-replays`; server exposes **`POST /v1/sessions/listing`** and per-session **`/v1/sessions/{sessionId}/snapshots-*`** |
| Query builder MCP (`get_query_tables`, `get_query_history`, `get_query_stats`, `get_universal_query_tables`, `get_universal_query_columns`) | **removed** | Tools **unregistered** with product — historical **404** (`/v2/*`), **500** (missing `user-email`) in sections below |
| `get_anomaly_details`, `get_anomaly_apdex`, `get_anomaly_error_rate` | **404** | No `/anomaly/*` REST resources in pulse-server (**MCP tools removed 2026-05-14**) |
| `get_alert_metrics` | **400** | **`scope`** query parameter is **required** (`@NotNull`); MCP calls bare `/v1/alert/metrics` |
| `get_heatmap_data` | **403** | URL matches server, but **`HeatmapServiceImpl`** returns **`FORBIDDEN`** when heatmap feature is off or `sessionSampleRate` is not **> 0** in active SDK config |

---

## Interaction metrics (`metrics.ts`) → HTTP 404

**MCP calls**

- `POST /v3/metric/getApdexScore`
- `POST /v3/metric/getErrorRate`
- `POST /v3/metric/composite/getInteractionTime`
- `POST /v3/metric/composite/getInteractionCategory`

Definitions in `pulse-mcp/src/tools/metrics.ts` mirror `pulse-ui` (`Constants.ts` still lists the same `/v3/metric/...` paths).

**pulse-server**

There is **no** `@Path("/v3/metric` …)** (or equivalent string registration under `v3/metric`) in `backend/server/src/main/java`. Closest related metric surface is **`POST /v1/interactions/performance-metric/distribution`** (`PerformanceMetricDistribution.java`), which uses a different contract (`QueryRequest`), not the four MCP POST bodies.

**Conclusion:** Requests hit pulse-server as defined in this repo → **no matching resource** → **404**. Fixing MCP without code changes is impossible; alignment would require routing/MCP/server contract work outside this doc.

---

## Session replays (`sessions.ts`) → HTTP 404

**MCP**

- `GET /v1/session-replays` with query params (`pulse-mcp/src/tools/sessions.ts`).

**pulse-server**

- **Listing:** `SessionListingResource` — **`POST /v1/sessions/listing`** (JSON body `SessionListingRequest`), plus **`GET /v1/sessions/filters`**.
- **Replay blobs:** `SessionReplay` — **`GET /v1/sessions/{sessionId}/snapshots-source`** and **`GET /v1/sessions/{sessionId}/snapshots-data`**.

There is **no** `session-replays` path string in `backend/server`.

**Conclusion:** MCP path/method **do not exist** on pulse-server → **404**. The UI constant `/v1/session-replays` is similarly out of sync with the Java routes (listing is POST under `/v1/sessions`).

---

## Universal SQL (`query.ts`) → HTTP 404

**MCP**

- `GET /v2/getListOfTables`
- `GET /v2/getColumnNamesOfTable`

**pulse-server**

No Java references to `getListOfTables` / `getColumnNamesOfTable`. The supported discovery endpoint is **`GET /query/tables`** (`GetTablesAndColumns.java`), which returns tables **with embedded column metadata** (historically exposed via MCP **`get_query_tables`** / **`get_universal_query_*`**, now **removed**).

**Conclusion:** `/v2/*` helpers are **not implemented** on pulse-server in this repo → **404**.

---

## Anomaly (formerly `anomaly.ts`) → HTTP 404 / tools removed

**MCP (historical)**

- `GET /anomaly/details`
- `GET /anomaly/apdex`
- `GET /anomaly/error-rate`

These tools were **removed from MCP registration** on **2026-05-14** because pulse-server still has no matching routes.

**pulse-server**

No anomaly REST controllers under `resources/` for these paths.

**Conclusion:** **404** when they existed — endpoints absent from pulse-server as checked into this monorepo.

---

## Alerts (`alerts.ts`) — `get_alert_metrics` → HTTP 400

**MCP**

- `GET /v1/alert/metrics` with **no query string**.

**pulse-server**

`GetAlertMetrics` requires **`@QueryParam("scope") @NotNull String scope`**.

**Conclusion:** Jakarta validation / missing required param → **400**. MCP needs to pass a scope (e.g. values aligned with alert scopes / DB `alert_metrics.scope`), not just `projectId` in headers.

---

## Query builder (`query.ts`) — history & stats → HTTP 500

**MCP**

- `GET /query/history?limit&offset`
- `GET /query/stats` (no dates)

**pulse-server**

Both controllers require **`@HeaderParam("user-email") String userEmail`**:

- `GetQueryHistory`
- `GetQueryStatistics` (also optional `startDate` / `endDate`; defaults exist **only if** email is valid)

**MCP client**

`pulse-mcp/src/client.ts` sets `Authorization`, `Accept`, `Content-Type`, and optionally `X-Project-ID` — **no `user-email`**.

**Services**

- `QueryServiceImpl.getQueryHistory` throws if email null/blank.
- `QueryStatisticsService.getQueryStatistics` throws if email null/blank.

**UI parity**

`pulse-ui` adds `user-email` via `makeRequestToServer` / `authenticateUser`.

**Conclusion:** Missing header → backend error path → observed **500** from MCP (exact mapping depends on the global exception handler). Root cause: **contract mismatch** between MCP client and query endpoints.

---

## Heatmaps (`heatmap.ts`) → HTTP 403

**MCP**

- `GET /v1/heatmap/data` — path and query param names align with `HeatmapController` (`screenName`, `from`, `to`, etc.).

**pulse-server behavior**

`HeatmapServiceImpl.getHeatmapData` loads active SDK config and, if **`!isHeatmapFeatureEnabled(cfg)`**, returns **`ServiceError.FORBIDDEN`** with message **“Heatmaps are disabled for this project”**. Enablement requires feature **`heatmap`** with **`sessionSampleRate != null && sessionSampleRate > 0.0`**.

**Conclusion:** **403** is **not** a wrong URL in MCP; it is **project/SDK configuration** (heatmap feature effectively off). Different principals or projects may succeed.

---

**How to fix (proposed remedies):** [`mcp-tools-fixes.md`](./mcp-tools-fixes.md).

---

## pulse-mcp code fixes applied (2026-05-14)

The following address the gaps above in **`pulse-mcp`** (not pulse-server). Re-run verification against your pulse-server environment when contracts change.

| Issue area | Change |
|------------|--------|
| Interaction metrics | Tools call **`POST /v1/interactions/performance-metric/distribution`** with `QueryRequest`-shaped JSON (`TIME_BUCKET`, filters `PulseType` / `SpanName`). **`interactionId`** documents span **name**. |
| Session listing | **`POST /v1/sessions/listing`** with `SessionListingRequest` JSON. |
| Universal SQL discovery | **`GET /query/tables`**; columns filtered client-side. |
| Alert metrics | Required MCP arg **`scope`** → `?scope=`. |
| Query history/stats | **`user-email`** header from JWT **`email`** claim (`jwtEmail.ts`). |
| Heatmaps | Tool description + axios error JSON surfacing (403 still possible). |
| Anomaly tools | **Removed** from MCP registration (no backend routes). |

Historical sections earlier in this file describe **pre-fix** failures for context.

---

## Related files (for owners)

| Area | pulse-mcp | pulse-server |
|------|-----------|--------------|
| Metrics | `pulse-mcp/src/tools/metrics.ts` | (no `/v3/metric` controller); `PerformanceMetricDistribution.java` |
| Sessions | `pulse-mcp/src/tools/sessions.ts` | `SessionListingResource.java`, `SessionReplay.java` |
| Universal query | *(MCP tools removed)* | `GetTablesAndColumns.java` (`/query/tables`) if backend routes remain |
| Anomaly | *(tools removed)* | (none located) |
| Alert metrics | `pulse-mcp/src/tools/alerts.ts` | `GetAlertMetrics.java` |
| Query history/stats | *(MCP tools removed)* | `GetQueryHistory.java`, `GetQueryStatistics.java`, `QueryServiceImpl.java`, `QueryStatisticsService.java` |
| Heatmap | `pulse-mcp/src/tools/heatmap.ts` | `HeatmapController.java`, `HeatmapServiceImpl.java` |

When MCP tools or backend routes change, update this file alongside [`mcp-tools.md`](./mcp-tools.md).
