# pulse-mcp — breaking changes and pending items

Client integrations, prompts, and automations that assumed the **pre–2026-05-14** tool contracts should be reviewed against this list.

---

## Breaking MCP contract (tool behavior / availability)

| Change | Detail |
|--------|--------|
| **`get_alert_metrics` requires `scope`** | Pulse-server expects **`GET /v1/alert/metrics?scope=...`** (`@NotNull`). The MCP tool now requires a **`scope`** argument (e.g. `interaction`). Call **`get_alert_scopes`** for valid values. Calls with only `projectId` will fail with **400**. |
| **`get_anomaly_*` tools removed** | `get_anomaly_details`, `get_anomaly_apdex`, and `get_anomaly_error_rate` are **no longer registered**. There is no **`/anomaly/*`** API on pulse-server in this repo; remove references from agents and scripts. |
| **Query builder MCP tools removed** | **`get_query_tables`**, **`get_query_history`**, **`get_query_stats`**, **`get_universal_query_tables`**, **`get_universal_query_columns`** are **no longer registered** — query builder was removed from the product. Remove references from agents and scripts. |
| **Interaction metrics: `interactionId` is span name** | **`get_apdex_score`**, **`get_error_rate`**, **`get_interaction_time`**, and **`get_interaction_categorization`** filter by **`SpanName`** using the **`interactionId`** parameter — use the **critical interaction name** as shown in the UI / telemetry, **not** the numeric DB id unless it happens to equal the span name. |
| **`get_interaction_root_cause` still uses backend interaction id** | It calls **`GET /v1/interactions/{interactionId}/root-cause`**. **`interactionId` here is the interaction resource id** (as from **`list_interactions`** / backend), **different** from the span-name semantics used by the four metric tools above. |

Other noteworthy behavior (not always “breaking”, but easy to misread):

- **`list_session_replays`** — **`POST /v1/sessions/listing`**. **`pageSize`** is capped at **100** server-side. Provide **`startTime` and `endTime` together** or omit both (defaults to last 7 days). Optional **`cursor`** for paging.
- **`get_heatmap_data`** — On HTTP errors (including **403** when heatmaps are disabled in SDK config), the tool may still return **MCP success** with JSON containing **`httpStatus`** and **`pulseError`** instead of failing the tool.

---

## Pending (tracked elsewhere)

- **Live verification:** Replace **“Pending subagent verify”** statuses in [`mcp-tools.md`](./mcp-tools.md) after exercising tools against your environment via Cursor MCP (or another client).
- **Historical docs:** [`mcp-tools-failures.md`](./mcp-tools-failures.md) still describes **pre-fix** failure modes for context; cross-check against current handlers under **`pulse-mcp/src/tools/`** when debugging.

---

## Related docs

- [`mcp-tools.md`](./mcp-tools.md) — tool inventory and verification workflow  
- [`mcp-tools-failures.md`](./mcp-tools-failures.md) — why tools failed before fixes  
- [`mcp-tools-fixes.md`](./mcp-tools-fixes.md) — remediation narrative  
