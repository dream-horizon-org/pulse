# pulse-mcp — proposed fixes for errored tools

This document proposes **remediation options** for each MCP tool that returned an HTTP error in live verification (project **`fancode`**, 2026-05-14). Root causes are summarized in [`mcp-tools-failures.md`](./mcp-tools-failures.md); tool inventory and status are in [`mcp-tools.md`](./mcp-tools.md).

**Implementation:** MCP-side fixes from this doc were landed **2026-05-14** — see [`mcp-tools-failures.md`](./mcp-tools-failures.md) § “pulse-mcp code fixes applied”. **`get_heatmap_data`** may still return **403** for disabled heatmaps.

**Method (original):** Four parallel read-only / analysis passes each consumed [`mcp-tools.md`](./mcp-tools.md) and [`mcp-tools-failures.md`](./mcp-tools-failures.md) plus targeted repo context (`pulse-mcp/src/tools/*.ts`, `pulse-server` JAX-RS). Findings were merged into this single owner doc.

---

## Implementation status (2026-05-14)

The **recommended MCP-side fixes** in this doc were implemented in `pulse-mcp` (metrics → distribution, sessions POST listing, alert `scope`, heatmap UX, anomaly tools unregistered). **Query builder MCP tools were later removed** when that product feature retired (sections below remain historical). **`get_heatmap_data`** may still return **403** when SDK config disables heatmaps — expected until configuration changes.

---

## Summary

| Tool | Observed | Primary fix owner | Recommended direction |
|------|----------|-------------------|----------------------|
| `get_apdex_score` | 404 | MCP **or** backend | Prefer **MCP → `POST /v1/interactions/performance-metric/distribution`** with `QueryRequest` selecting APDEX; optional backend **`/v3/metric/*` adapters** only if legacy callers must stay on old paths |
| `get_error_rate` | 404 | MCP **or** backend | Same pattern — **`distribution`** + **`ERROR_RATE`** (or equivalent `Functions`); optional **`/v3/metric/getErrorRate`** shim |
| `get_interaction_time` | 404 | MCP **or** backend | **`distribution`** + duration percentiles (`DURATION_P50` / `P95` / `P99`, etc.); optional **`/v3/metric/composite/getInteractionTime`** shim |
| `get_interaction_categorization` | 404 | MCP **or** backend | **`distribution`** + **`USER_CATEGORY_*`** (and related rates); optional **`/v3/metric/composite/getInteractionCategory`** shim |
| `list_session_replays` | 404 | MCP | **POST `/v1/sessions/listing`** + `SessionListingRequest` body; retire **`GET /v1/session-replays`** |
| `get_universal_query_tables` | 404 | MCP | Call **`GET /query/tables`** (same as **`get_query_tables`**) or deprecate redundant tool |
| `get_universal_query_columns` | 404 | MCP | Same — **`/query/tables`** embeds columns; or deprecate |
| `get_anomaly_details` | 404 | Product / backend | Implement **`/anomaly/*`** on pulse-server **or** remove/hide MCP tools until API exists; UI today is constants/mocks only |
| `get_anomaly_apdex` | 404 | Product / backend | Same |
| `get_anomaly_error_rate` | 404 | Product / backend | Same |
| `get_alert_metrics` | 400 | MCP | Add required **`scope`** query param (from **`get_alert_scopes`** / alert vocabulary) |
| `get_query_history` | 500 | MCP **or** backend | Send **`user-email`** header from authenticated identity **or** resolve user from JWT server-side |
| `get_query_stats` | 500 | MCP **or** backend | Same as query history |
| `get_heatmap_data` | 403 | Config (+ optional MCP UX) | Enable heatmap feature + **`sessionSampleRate > 0`** in active SDK config; improve tool messaging on 403 |

---

## Interaction metrics (`metrics.ts`) — HTTP 404

**Shared context:** MCP posts bodies aligned with legacy **`POST /v3/metric/*`**. pulse-server has **no** `/v3/metric` JAX-RS registration in this repo. The supported surface is **`POST /v1/interactions/performance-metric/distribution`** (`PerformanceMetricDistribution`) using **`QueryRequest`** (`dataType`, `timeRange`, `select`, `filters`, optional `groupBy` / `orderBy`). UI primary path uses distribution; some UI constants still mention `/v3/metric/...` (parity with MCP breakage).

### `get_apdex_score`

- **Root cause:** **`POST /v3/metric/getApdexScore`** is not registered → **404**.
- **Fix options (ranked):** **A)** Backend compatibility routes under `/v3/metric/...` delegating to distribution logic; **B)** Retarget MCP to **`distribution`** with APDEX in **`select`** and interaction filters / time bucketing aligned with UI graphs; **C)** Deprecate tool and document **`distribution`** only.
- **Recommendation:** **B** unless external **`/v3`** compatibility is mandatory — then **A**.
- **Verification:** **200** from **`distribution`** for a known interaction/window; values consistent with interaction analytics UI.

### `get_error_rate`

- **Root cause:** **`POST /v3/metric/getErrorRate`** missing → **404**.
- **Fix options:** **A)** Thin **`/v3/metric/getErrorRate`** adapter; **B)** **`distribution`** with **`ERROR_RATE`** (or equivalent interaction error **`Functions`**); **C)** Deprecate.
- **Recommendation:** **B** (same as APDEX).
- **Verification:** **200** + **`ERROR_RATE`** series matches UI for same filters.

### `get_interaction_time`

- **Root cause:** **`POST /v3/metric/composite/getInteractionTime`** missing → **404**.
- **Fix options:** **A)** Restore **`/v3`** composite route; **B)** **`distribution`** with percentile **`select`** (`DURATION_P50`, `DURATION_P95`, `DURATION_P99`, etc.) mirroring **`useGetInteractionDetailsGraphs`** / hooks; **C)** Deprecate.
- **Recommendation:** **B**.
- **Verification:** Percentiles match interaction detail charts.

### `get_interaction_categorization`

- **Root cause:** **`POST /v3/metric/composite/getInteractionCategory`** missing → **404**.
- **Fix options:** **A)** **`/v3`** adapter mapping to **`USER_CATEGORY_*`** / rates; **B)** **`distribution`** multi-function **`select`** for category breakdown; **C)** Deprecate.
- **Recommendation:** **B**.
- **Verification:** Bucket counts/rates match UI categorization widget.

**Cross-check:** Re-run MCP verification in [`mcp-tools.md`](./mcp-tools.md); metric tools should move from **404** to **2xx**.

---

## Session replays (`sessions.ts`) — HTTP 404

### `list_session_replays`

- **Root cause:** MCP uses **`GET /v1/session-replays`**; server exposes **`POST /v1/sessions/listing`** (`SessionListingResource`) with **`SessionListingRequest`** JSON — no `session-replays` path.
- **Fix:** **MCP-only** — switch method/path/body to listing contract. Backend route exists.
- **Arg mapping:** Legacy flat query params (`interactionName`, `startTime`, `endTime`, `page`, `pageSize`, …) do **not** map 1:1. Server expects **`timeRange.from` / `timeRange.to`** (required), **`page.limit`** and **`page.cursor`** (cursor pagination, not offset pages), optional **`filters.quick` / `filters.advanced`**, **`query`**, **`sortBy`**, **`sortDirection`**.
- **Verification:** Compare `pulse-mcp/src/tools/sessions.ts` to `SessionListingResource` `@POST @Path("/listing")`.

---

## Universal SQL discovery (`query.ts`) — HTTP 404

### `get_universal_query_tables`

- **Root cause:** **`GET /v2/getListOfTables`** not implemented → **404**.
- **Fix:** **MCP-only** — use **`GET /query/tables`** (`GetTablesAndColumns`), identical to working **`get_query_tables`**.
- **Product note:** Consider deprecating this tool to avoid duplicate MCP surface.

### `get_universal_query_columns`

- **Root cause:** **`GET /v2/getColumnNamesOfTable`** not implemented → **404**.
- **Fix:** **MCP-only** — **`GET /query/tables`** returns tables **with embedded column metadata**; implement column lookup client-side or merge into one discovery tool.
- **Verification:** `query.ts` `/v2/*` vs **`GetTablesAndColumns`**.

---

## Anomaly detection (removed from MCP)

**Repo note:** No **`/anomaly/*`** JAX-RS in **`backend/server`**; **`pulse-ui`** references these paths in **`Constants.ts`** and mocks — not a live pulse-server API in this monorepo. **`pulse-mcp` no longer registers these tools** (2026-05-14).

- **Chosen fix (2026-05-14):** **2)** — tools **unregistered**; `anomaly.ts` deleted.

### `get_anomaly_details` / `get_anomaly_apdex` / `get_anomaly_error_rate`

- **Root cause:** **`GET /anomaly/details`**, **`GET /anomaly/apdex`**, **`GET /anomaly/error-rate`** have no server resources → **404**.
- **Fix options:** **1)** Implement REST + backing queries on pulse-server; **2)** Unregister or hide MCP tools until backend exists; **3)** Point MCP at another service if anomaly is hosted elsewhere (Kong routing).
- **Recommended interim:** Document **unsupported**; distinguish from broken **`get_apdex_score`** / **`get_error_rate`** (**`/v3/metric/*`**, different failure mode).
- **Verification:** HTTP probe remains **404** until **1)** or **3)**; grep **`backend/server`** for anomaly controllers.

---

## Alerts (`alerts.ts`) — HTTP 400

### `get_alert_metrics`

- **Root cause:** **`GET /v1/alert/metrics`** without **`scope`** violates **`@QueryParam("scope") @NotNull`** → **400**.
- **Fix:** **MCP-only** — add tool argument **`scope`** → **`?scope=`**.
- **Scope sourcing:** Values from **`get_alert_scopes`** (`GET /v1/alert/scopes`) or same vocabulary as **`list_alerts`** / filters (example in **`backend/server/README.md`**: **`scope=interaction`**).
- **Verification:** `GetAlertMetrics.java` vs updated MCP handler.

---

## Query builder (`query.ts`) — HTTP 500

### `get_query_history`

- **Root cause:** **`GET /query/history`** requires **`user-email`** header; **`pulse-mcp/src/client.ts`** does not send it → service throws on blank email → observed **500** (exact mapping via global exception handler).
- **Fix options:** **1)** MCP sets **`user-email`** after PAT exchange (from JWT claims or **`/v1/users/me`**-style endpoint); **2)** Backend derives actor from JWT and drops header requirement.
- **Consistency / security:** If header is kept, server must validate it matches authenticated user (avoid impersonation). JWT-only avoids duplicated PII on every request.
- **Verification:** **200** with empty or populated history; forged header → **400/403**, not **500**.

### `get_query_stats`

- **Root cause:** Same as history — **`GetQueryStatistics`** + **`user-email`**; optional date params only apply once email is valid.
- **Fix:** Same **1)** / **2)** as **`get_query_history`**.
- **Verification:** **200** for stats; UI parity unchanged.

---

## Heatmaps (`heatmap.ts`) — HTTP 403

### `get_heatmap_data`

- **Root cause:** URL matches **`HeatmapController`**; **403** from **`HeatmapServiceImpl`** when heatmap feature off or **`sessionSampleRate`** not **> 0** in active SDK config (“Heatmaps are disabled for this project”).
- **Fix options:** **1)** Enable **`heatmap`** feature + positive **`sessionSampleRate`** via SDK configuration (`get_active_sdk_config` / UI); **2)** MCP/tool description surfaces server message on **403** so operators do not misread as auth failure.
- **Verification:** Disabled project → **403** + message (expected); enabled project + valid **`screenName`/window** → **200**.

---

## Related files

| Area | pulse-mcp | pulse-server |
|------|-----------|--------------|
| Metrics | `src/tools/metrics.ts` | `PerformanceMetricDistribution.java` |
| Sessions | `src/tools/sessions.ts` | `SessionListingResource.java`, `SessionReplay.java` |
| Universal SQL | `src/tools/query.ts` | `GetTablesAndColumns.java` |
| Anomaly | *(tools unregistered; `anomaly.ts` deleted)* | (none today) |
| Alerts | `src/tools/alerts.ts` | `GetAlertMetrics.java` |
| Query history/stats | `src/tools/query.ts`, `src/client.ts` | `GetQueryHistory.java`, `GetQueryStatistics.java`, query services |
| Heatmap | `src/tools/heatmap.ts` | `HeatmapController.java`, `HeatmapServiceImpl.java` |

When implementing fixes, update [`mcp-tools.md`](./mcp-tools.md) live verification tables and trim or extend [`mcp-tools-failures.md`](./mcp-tools-failures.md) as behavior changes.
