# pulse-mcp — MCP tools reference

The Pulse MCP server (`pulse-mcp`) registers tools only (no prompts/resources in `src/index.ts`). It speaks MCP over **stdio** and proxies **pulse-server** REST APIs using credentials from `POST /v1/auth/api-key/exchange` (see repo `pulse-mcp/README.md`).

**Total tools: 45** (three anomaly tools removed; five query-builder tools removed — product UI retired; see [`mcp-tools-failures.md`](./mcp-tools-failures.md)).

---

## Live verification

### Post-fix (repo state)

**When:** 2026-05-14 — MCP handlers updated to align with pulse-server (distribution metrics, session listing, alert `scope`). **Anomaly tools unregistered.** **Query builder MCP tools removed** when that product surface was retired.

**Summary (expected after verification)**

| Outcome | Expected |
|--------|----------|
| OK | All tools except possible conditional rows below |
| Conditional | `get_heatmap_data` — **403** when heatmaps disabled in SDK config (tool returns JSON with `httpStatus`); **`get_interaction_root_cause`** may return empty/`noDataAvailable` for some windows |

### Historical pre-fix snapshot (2026-05-14 UTC, before MCP changes)

**Project:** `fancode`. Several tools returned 404/400/500 due to wrong routes or missing headers — see [`mcp-tools-failures.md`](./mcp-tools-failures.md).

---

## Projects (`src/tools/projects.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_projects` | List all Pulse projects accessible to the authenticated user | OK |
| `get_project` | Get details of a specific Pulse project | OK |
| `list_project_members` | List members of a Pulse project with their roles | OK |

---

## Interactions (`src/tools/interactions.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_interactions` | List user interactions (critical interactions) with optional filters and pagination | OK |
| `list_suggested_interactions` | List AI-suggested interactions for a project | OK |
| `get_interaction_filter_options` | Filter options for the interaction list | OK |
| `get_interaction_telemetry_filters` | Telemetry filters (platform, app version, OS, etc.) | OK |
| `get_interaction_root_cause` | Root cause analysis (RCA) for a specific interaction — **`interactionId` is the backend/MySQL interaction id** (not span name; differs from metrics tools) | OK (JSON `noDataAvailable` for sampled window) |

---

## Event catalog (`src/tools/events.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_event_definitions` | Event definitions with optional search and category filtering | OK |
| `get_event_definition` | Single event definition by ID | OK |
| `list_event_categories` | Distinct event categories in the project | OK |
| `search_events` | Search events by name (autocomplete-style) | OK |

---

## Interaction metrics (`src/tools/metrics.ts`)

Uses **`POST /v1/interactions/performance-metric/distribution`**. **`interactionId`** must be the **interaction span name** (same as UI critical interaction name), not the numeric MySQL id.

| Tool | Description | Status |
|------|-------------|--------|
| `get_apdex_score` | APDEX time series (`TIME_BUCKET` + `APDEX`) | Pending subagent verify |
| `get_error_rate` | Error rate time series (`TIME_BUCKET` + `ERROR_RATE`) | Pending subagent verify |
| `get_interaction_time` | P50 / P95 / P99 for window | Pending subagent verify |
| `get_interaction_categorization` | User category counts (excellent/good/average/poor) | Pending subagent verify |

---

## Session replays (`src/tools/sessions.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_session_replays` | **`POST /v1/sessions/listing`**; optional `interactionName` as search query; `pageSize` max **100** (server cap) | Pending subagent verify |

---

## Funnels (`src/tools/funnels.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_funnels` | Funnels with optional search, status, and tag filters | OK |
| `get_funnel` | Details of a specific funnel | OK |
| `get_funnel_tags` | Tags used across funnels and journeys | OK |
| `get_funnel_events` | Events available for funnel steps | OK |
| `get_funnel_filters` | Filter fields for funnel analysis | OK |

---

## Journeys (`src/tools/journeys.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_journeys` | User journeys with optional search, status, and tag filters | OK |
| `get_journey` | Details of a specific user journey | OK |

---

## Alerts (`src/tools/alerts.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_alerts` | Configured alerts for a project | OK |
| `get_alert_evaluation_history` | Evaluation history for a specific alert | OK |
| `get_alert_filters` | Available alert filter options | OK |
| `get_alert_scopes` | Alert scopes (what can be alerted on) | OK |
| `get_alert_metrics` | Metrics usable in alert conditions (**`scope` required**, e.g. `interaction`) | Pending subagent verify |
| `get_alert_severities` | Available severity levels | OK |
| `list_alert_notification_channels` | Notification channels for alerts | OK |

---

## Heatmaps (`src/tools/heatmap.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `get_heatmap_data` | Heatmap data; **403** may mean SDK heatmaps off — response JSON includes `httpStatus` / `pulseError` | Conditional (often 403 if heatmaps disabled) |

---

## SDK configuration (`src/tools/sdkConfig.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `get_active_sdk_config` | Currently active SDK configuration | OK |
| `list_sdk_configs` | All SDK configuration versions | OK |
| `get_sdk_config` | Specific SDK configuration version | OK |
| `get_sdk_rules_features` | Available SDK rules and feature flags | OK |

---

## Removed tools

The following were **removed from MCP registration**:

| Removal | Tools |
|---------|--------|
| No pulse-server API | `get_anomaly_details`, `get_anomaly_apdex`, `get_anomaly_error_rate` |
| Query builder feature retired | `get_query_tables`, `get_query_history`, `get_query_stats`, `get_universal_query_tables`, `get_universal_query_columns` |

---

## App Vitals (`src/tools/appVitals.ts`)

| Tool | Description | Status |
|------|-------------|--------|
| `list_app_vitals_crash_issues` | Crash issues (`device.crash`), distribution-style list | OK |
| `list_app_vitals_anr_issues` | ANR issues (`device.anr`) | OK |
| `list_app_vitals_nonfatal_issues` | Non-fatal issues (`non_fatal`) | OK |
| `get_app_vitals_user_session_totals` | Unique users/sessions from `session.start` logs | OK |
| `get_app_vitals_issue_summary` | Summary for one exception `groupId` | OK |
| `get_app_vitals_issue_trend` | Time-bucketed counts; `trendView`: `aggregated` \| `appVersion` \| `os` | OK |
| `get_app_vitals_issue_stack_traces` | Sample raw exception rows for a `groupId` | OK |
| `get_app_vitals_issue_screen_breakdown` | Top screens by occurrence for a `groupId` | OK |
| `get_app_vitals_exception_first_last_seen` | First/last seen per `groupId` (max 50 IDs, ~6-month window) | OK |

---

## Implementation notes

- Tool handlers live under `pulse-mcp/src/tools/`.
- Registration order matches `pulse-mcp/src/index.ts`.
- Responses are JSON serialized into MCP **text** content.

When adding or renaming tools, update this document to match `src/tools/*.ts`.

**Failure analysis (historical):** [`mcp-tools-failures.md`](./mcp-tools-failures.md).

**Remediation log / options:** [`mcp-tools-fixes.md`](./mcp-tools-fixes.md).

**Breaking changes and pending items:** [`mcp-breaking-changes-and-pending.md`](./mcp-breaking-changes-and-pending.md).
