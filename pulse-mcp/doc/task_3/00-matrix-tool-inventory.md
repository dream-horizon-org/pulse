# Tool inventory and risk tags

Substitution: `{PROJECT}`, `{PROJECT_FORBIDDEN}` (valid ID, no view perm), placeholders per domain files.

**Domain groups** — tools within a domain share an `interactionId` semantics trap; see `[P]` column.

### Projects (3)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
| 1 | `list_projects` | GET `/v1/users/me/projects` | Baseline auth | EVAL-001 |
| 2 | `get_project` | GET `/v1/projects/{id}` X-Project-ID | 403 vs typo ID | EVAL-002 |
| 3 | `list_project_members` | GET `/v1/projects/{id}/members` | May expose emails | EVAL-003 |

### Interactions + Analytics (9)

> **`interactionId` has two incompatible meanings within this domain** — the single biggest `[P]` risk:
> - **Metrics tools** (rows 4–7): `interactionId` = **SpanName** string from telemetry
> - **RCA tool** (row 8): `interactionId` = **DB entity id** from `list_interactions`

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
| 4 | `list_interactions` | GET `/v1/interactions` | Discovery — returns DB entity ids | EVAL-021, EVAL-032, EVAL-055, EVAL-056, EVAL-058, SCN-C |
| 5 | `list_suggested_interactions` | GET `/v1/interactions/suggestions` | AI-suggested, not the monitored list | EVAL-004, EVAL-055 |
| 6 | `get_interaction_filter_options` | GET `/v1/interactions/filter-options` | Statuses/emails for list UI filters | EVAL-005, EVAL-053 |
| 7 | `get_interaction_telemetry_filters` | GET `/v1/interactions/telemetry-filters` | Platform/appVersion/OS for dashboard | EVAL-013 |
| 8 | `get_interaction_root_cause` | GET `/v1/interactions/{interactionId}/root-cause` | `interactionId` = **DB entity id** from `list_interactions` **`[P]`** | EVAL-021, EVAL-026, EVAL-032, EVAL-056, EVAL-058, SCN-C |
| 9 | `get_interaction_apdex_score` | POST `/v1/interactions/performance-metric/distribution` | `interactionId` = **SpanName** **`[P]`** | EVAL-017, EVAL-027, EVAL-048, EVAL-057, SCN-B |
|10 | `get_interaction_error_rate` | POST … distribution | `interactionId` = **SpanName** **`[P]`** | EVAL-017, EVAL-041, SCN-B |
|11 | `get_interaction_time` | POST … distribution | `interactionId` = **SpanName** **`[P]`** | EVAL-017 |
|12 | `get_interaction_categorization` | POST … distribution | `interactionId` = **SpanName** **`[P]`** | EVAL-017 |

### Sessions (1)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|13 | `list_session_replays` | POST `/v1/sessions/listing` | optional `user-email` from JWT **`[P]`**; both times or neither | EVAL-018, EVAL-031, EVAL-036, EVAL-042, SCN-F |

### Events (4)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|14 | `list_event_definitions` | GET `/v1/event-definitions` | offset/limit; has `search` param | EVAL-040 |
|15 | `get_event_definition` | GET `/v1/event-definitions/{id}` | numeric id lookup | EVAL-040 |
|16 | `list_event_categories` | GET `/v1/event-definitions/categories` | | EVAL-009 |
|17 | `search_events` | GET `/v1/events` (`search_string`) | autocomplete-style; maps `searchString` → snake | EVAL-040 |

### Funnels (5)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|18 | `list_funnels` | GET `/v1/funnels` | page 1-based (≠ `list_interactions` page 0) | EVAL-020, EVAL-035, EVAL-054 |
|19 | `get_funnel` | GET `/v1/funnels/{funnelId}` | | EVAL-020, EVAL-043 |
|20 | `get_funnel_tags` | GET `/v1/funnels/tags` | lists tag vocabulary; does not filter | EVAL-010, EVAL-054 |
|21 | `get_funnel_events` | GET `/v1/funnels/events` | events for funnel step builder | EVAL-011 |
|22 | `get_funnel_filters` | GET `/v1/funnels/filters` | filter fields for funnel analysis | EVAL-012 |

### Journeys (2)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|23 | `list_journeys` | GET `/v1/journeys` | ≠ funnels; different concept | EVAL-034, EVAL-043 |
|24 | `get_journey` | GET `/v1/journeys/{journeyId}` | | EVAL-034, EVAL-043 |

### Alerts (7)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|25 | `list_alerts` | GET `/v1/alert` | | EVAL-022, EVAL-039, SCN-E |
|26 | `get_alert_evaluation_history` | GET `/v1/alert/{alertId}/evaluationHistory` | per-alert detail, not a list | EVAL-022, EVAL-046, SCN-E |
|27 | `get_alert_filters` | GET `/v1/alert/filters` | | EVAL-039 |
|28 | `get_alert_scopes` | GET `/v1/alert/scopes` | must call before `get_alert_metrics` | EVAL-014, EVAL-023, EVAL-033 |
|29 | `get_alert_metrics` | GET `/v1/alert/metrics?scope=` | **`scope` required** **`[P]`** | EVAL-023, EVAL-033 |
|30 | `get_alert_severities` | GET `/v1/alert/severity` | | EVAL-006 |
|31 | `list_alert_notification_channels` | GET `/v1/alert/notificationChannels` | read-only; no mutation via MCP | EVAL-007 |

### Heatmap (1)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|32 | `get_heatmap_data` | GET `/v1/heatmap/data` | 403 = feature flag off, not auth **`[P]`** | EVAL-019, EVAL-030, SCN-D |

### SDK Config (4)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|33 | `get_active_sdk_config` | GET `/v1/configs/active` **raw** envelope | differs from `{data}` unwrap | EVAL-019, EVAL-030, EVAL-044, SCN-D |
|34 | `list_sdk_configs` | GET `/v1/configs` | all versions, not just active | EVAL-047, EVAL-052 |
|35 | `get_sdk_config` | GET `/v1/configs/{version}` | specific version by number | EVAL-047 |
|36 | `get_sdk_rules_features` | GET `/v1/configs/rules-features` | | EVAL-008 |

### App Vitals (9)

| # | MCP tool | Primary API (method path) | Risk / notes | Eval coverage |
|---|----------|---------------------------|--------------|---------------|
|37 | `list_app_vitals_crash_issues` | POST distribution EXCEPTIONS | top-N only; no offset | EVAL-016, EVAL-018, EVAL-024, EVAL-038, SCN-A |
|38 | `list_app_vitals_anr_issues` | POST distribution | same shape as crash list | EVAL-015, EVAL-029 |
|39 | `list_app_vitals_nonfatal_issues` | POST distribution | | EVAL-037, EVAL-045 |
|40 | `get_app_vitals_user_session_totals` | POST distribution LOGS | session.start denominator **`[P]`** | EVAL-024 |
|41 | `get_app_vitals_issue_summary` | POST distribution EXCEPTIONS | needs real `groupId` | EVAL-025, EVAL-049 |
|42 | `get_app_vitals_issue_trend` | POST distribution EXCEPTIONS | `trendView` enum | EVAL-025, EVAL-050 |
|43 | `get_app_vitals_issue_stack_traces` | POST distribution EXCEPTIONS | limit 1–50 | EVAL-016, EVAL-025 |
|44 | `get_app_vitals_issue_screen_breakdown` | POST distribution EXCEPTIONS | top screens, not time trend | EVAL-051 |
|45 | `get_app_vitals_exception_first_last_seen` | POST distribution EXCEPTIONS | max 50 IDs; `eventName` omit = non_fatal **`[P]`** | EVAL-028, EVAL-029, EVAL-037 |

**Count:** **44** tools (source exploration) — confirm with **`tools/list`** after build (authoritative snapshot).

