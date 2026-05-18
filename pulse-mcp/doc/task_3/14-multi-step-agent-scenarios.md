# Multi-step evaluation scenarios *(how models actually misuse tools)*

Run end-to-end in a chat session — **grading** correctness of final narrative **and** tool sequence. Recording **wrong tool picks** counts as FAIL even if eventual JSON looks colorful.

See `17-scoring-rubric.md` for scoring definitions. Each scenario now carries formal ground truth alongside the original reference flow.

Substitution: **`{PROJECT_READ}`**, real IDs from preceding steps.

---

## SCN-A — "Explain our top crash last week."

**Prompt:** "Explain our top crash from last week for project {PROJECT_READ}."

**Expected tools:** `[list_app_vitals_crash_issues, get_app_vitals_issue_summary, get_app_vitals_issue_stack_traces]`
**Must not pick:** `[list_interactions, get_interaction_error_rate, get_interaction_root_cause, get_interaction_apdex_score]`
**Min tools:** 3 **Max tools:** 6
**Confidence:** high

**Pass:** All three expected tools called. No interaction or metrics tools called. `get_app_vitals_issue_screen_breakdown` and `get_app_vitals_user_session_totals` are acceptable bonus calls (noise-call score 0.9 if only bonus tools added).

Goal: Chain **discovery → summary → attribution evidence**.

Suggested reference flow:

1. `list_projects` (if project unknown).
2. `list_app_vitals_crash_issues` `{ projectId, startTime, endTime: week window }`.
3. `get_app_vitals_issue_summary` top `group_id`.
4. `get_app_vitals_issue_screen_breakdown` same `groupId`.
5. `get_app_vitals_issue_stack_traces` capped `limit`.
6. `get_app_vitals_user_session_totals` (**denominator**) over same coarse window — optional but prevents mis-scaled percentages in prose.

**Common failure modes to flag**

- **`[P]`** Using **`interactionId`** from interactions list as **`interactionId`** in **`get_interaction_apdex_score`** (**wrong subsystem**).

---

## SCN-B — "Is interaction X regressing latency?" **`[P]`**

**Prompt:** "Is the CheckoutFlow interaction regressing in latency and errors for project {PROJECT_READ}? Compare the last 24 hours vs the previous 24 hours."

**Expected tools:** `[get_interaction_apdex_score, get_interaction_error_rate]`
**Must not pick:** `[get_interaction_root_cause, list_app_vitals_crash_issues, list_interactions]`
**Min tools:** 2 **Max tools:** 4
**Confidence:** high

**Pass:** Both `get_interaction_apdex_score` and `get_interaction_error_rate` called with "CheckoutFlow" as `interactionId` (span name, not DB id). `get_interaction_root_cause` not called.
**Parameter annotation:** Record whether `interactionId` is "CheckoutFlow" (correct span name) or a numeric id (wrong — DB entity id).

Reference flow:

1. Identify **`{SPAN_NAME}`** (telemetry / dashboards), **not** DB interaction UUID.
2. `get_interaction_apdex_score` + `get_interaction_error_rate` paired windows (prev vs curr) **same dimensions**.
3. Optional: `list_session_replays` + `interactionName` fragment correlated to problem sessions.

Failures: swapping span vs DB ID; widening window silently to erase regression.

---

## SCN-C — RCA exists for monitored interaction? **`[P]`**

**Prompt:** "Does the PaymentFlow interaction have a root cause analysis in project {PROJECT_READ}?"

**Expected tools:** `[list_interactions, get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, get_interaction_error_rate, list_app_vitals_crash_issues]`
**Min tools:** 2 **Max tools:** 2
**Confidence:** high

**Pass:** `list_interactions` called first to find the DB entity id; `get_interaction_root_cause` called with that id. No metrics tools called.
**Parameter annotation:** Record whether `interactionId` passed to `get_interaction_root_cause` is the row id from `list_interactions` response (correct) or the string "PaymentFlow" (wrong).

Reference flow:

1. `list_interactions`.
2. `get_interaction_root_cause` with **`interactionId` = row id returned by listings** (**validate field name empirically** — document it here after first dry run).

Failures: hallucinated RCA without calling **`get_interaction_root_cause`**; passing span name instead of DB id.

---

## SCN-D — Heatmap zeros — feature or permission? **`[P]`**

**Prompt:** "The heatmap for LoginScreen is showing zeros (or an error) in project {PROJECT_READ}. Is this a feature flag issue or a permission problem?"

**Expected tools:** `[get_active_sdk_config, get_heatmap_data]`
**Must not pick:** `[list_sdk_configs, get_sdk_rules_features]`
**Min tools:** 2 **Max tools:** 2
**Confidence:** high

**Pass:** `get_active_sdk_config` called before `get_heatmap_data`. Both called. Model correctly attributes 403 to SDK config (heatmap feature flag / sessionSampleRate = 0), not to an auth failure.

Mandatory order:

1. `get_active_sdk_config`.
2. `get_heatmap_data`.

Evaluator checks assistant explains **403** JSON body meaning vs empty heatmap artifact.

---

## SCN-E — Alert read-only guardrail *(read-only)*

**Prompt:** "Check the latency alert for project {PROJECT_READ} and tell me when it last fired."

**Expected tools:** `[list_alerts, get_alert_evaluation_history]`
**Must not pick:** `[get_alert_metrics, get_alert_filters]`
**Min tools:** 2 **Max tools:** 2
**Confidence:** high

**Pass:** `list_alerts` called to find alertId; `get_alert_evaluation_history` called. Model does not imply that alert creation, modification, or channel mutation is possible via MCP.

Reference flow:

1. `list_alerts` → **`alertId`**
2. `get_alert_evaluation_history`

Failure: implying channel mutations possible via MCP (**false** — MCP is read-only).

---

## SCN-F — Cursor pagination across sessions **`[P]`**

**Prompt:** "List sessions for project {PROJECT_READ} from the last hour in pages of 25. Show me the first two pages."

**Expected tools:** `[list_session_replays, list_session_replays]`
**Must not pick:** `[list_interactions, list_app_vitals_crash_issues]`
**Min tools:** 2 **Max tools:** 2
**Confidence:** high

**Pass:** `list_session_replays` called twice. Second call uses the `cursor` value from the first response. No duplicate sessions between pages.

Reference flow:

1. `list_session_replays` narrow hour window **`pageSize: 25`**, capture `cursor`.
2. Second call with same time range + `cursor`.

Failures: dumping duplicate sessions as "extra volume"; omitting **`cursor`** chaining; widening time range on second call.
