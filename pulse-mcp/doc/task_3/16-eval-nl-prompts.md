# LLM Tool-Selection Eval — Natural-Language Prompt Corpus

Each case grades **tool selection only** (not argument correctness — that belongs in files 01–13).
See `17-scoring-rubric.md` for scoring definitions.

Substitution placeholders used in Notes only; prompts are written as a user would actually type them.

**Categories:**
- `single` — one right tool, no plausible alternative
- `multi-step` — 2–4 tools in sequence
- `semantic-trap` — documented `[P]` footgun; wrong tool = hard fail
- `distractor-trap` — wrong-subsystem tool is plausible; tests description clarity
- `disambiguation` — two similar tools; context clue in prompt determines the right one

---

## Category 1 — Single-tool, unambiguous

---

### EVAL-001 — List all projects

**Prompt:** "List all the projects I have access to."

**Expected tools:** `[list_projects]`
**Must not pick:** `[get_project, list_project_members]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_projects` called. Neither `get_project` nor `list_project_members` called.
**Notes:** Baseline auth/routing sanity. Tests whether model understands "list all" maps to the no-arg list tool, not a per-project lookup.

---

### EVAL-002 — Get a specific project

**Prompt:** "Get the details of project fancode."

**Expected tools:** `[get_project]`
**Must not pick:** `[list_projects, list_project_members]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_project` called.
**Notes:** Tests "specific item" vs "list" routing.

---

### EVAL-003 — List project members

**Prompt:** "Who are the members of project fancode and what are their roles?"

**Expected tools:** `[list_project_members]`
**Must not pick:** `[get_project, list_projects]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_project_members` called.
**Notes:** "members / roles" is the discriminating phrase; `get_project` returns metadata, not members.

---

### EVAL-004 — List suggested interactions

**Prompt:** "What interactions does Pulse AI suggest I should monitor for project fancode?"

**Expected tools:** `[list_suggested_interactions]`
**Must not pick:** `[list_interactions, get_interaction_filter_options]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_suggested_interactions` called.
**Notes:** "AI-suggested" is the cue. Tests model doesn't default to `list_interactions`.

---

### EVAL-005 — Interaction filter options

**Prompt:** "What filter values are available for the interactions list in project fancode?"

**Expected tools:** `[get_interaction_filter_options]`
**Must not pick:** `[get_interaction_telemetry_filters, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_interaction_filter_options` called.
**Notes:** Probes whether model distinguishes "filter options for the list UI" vs "telemetry dimensions for dashboards".

---

### EVAL-006 — Alert severity levels

**Prompt:** "What alert severity levels are supported in project fancode?"

**Expected tools:** `[get_alert_severities]`
**Must not pick:** `[list_alerts, get_alert_filters]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_alert_severities` called.
**Notes:** Pure enum/metadata lookup. `get_alert_filters` is a broader umbrella — wrong choice here.

---

### EVAL-007 — Notification channels

**Prompt:** "Show me the notification channels configured for alerts in project fancode."

**Expected tools:** `[list_alert_notification_channels]`
**Must not pick:** `[list_alerts, get_alert_filters]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_alert_notification_channels` called.

---

### EVAL-008 — SDK rules and feature flags

**Prompt:** "What SDK rules and feature flags are available for project fancode?"

**Expected tools:** `[get_sdk_rules_features]`
**Must not pick:** `[get_active_sdk_config, list_sdk_configs]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_sdk_rules_features` called.
**Notes:** "rules and feature flags" is the discriminating phrase from the tool description.

---

### EVAL-009 — Event categories

**Prompt:** "List all distinct event categories in project fancode."

**Expected tools:** `[list_event_categories]`
**Must not pick:** `[list_event_definitions, search_events]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_event_categories` called.
**Notes:** "categories" is distinct from "definitions". Tests taxonomy vs catalog lookup.

---

### EVAL-010 — Funnel tags

**Prompt:** "What tags are used across funnels and journeys in project fancode?"

**Expected tools:** `[get_funnel_tags]`
**Must not pick:** `[list_funnels, list_journeys]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_funnel_tags` called.

---

### EVAL-011 — Events available for funnel steps

**Prompt:** "What events can I use when building funnel steps in project fancode?"

**Expected tools:** `[get_funnel_events]`
**Must not pick:** `[list_event_definitions, search_events, get_funnel_filters]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_funnel_events` called.
**Notes:** "for funnel steps" is the key phrase. Tests funnel-domain scoping vs generic event catalog.

---

### EVAL-012 — Funnel filter fields

**Prompt:** "What filter fields are available for funnel analysis in project fancode?"

**Expected tools:** `[get_funnel_filters]`
**Must not pick:** `[get_funnel_events, get_funnel_tags, list_funnels]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_funnel_filters` called.

---

### EVAL-013 — Telemetry filter dimensions

**Prompt:** "What telemetry dimensions like platform, app version, and OS version can I use to filter the dashboard in project fancode?"

**Expected tools:** `[get_interaction_telemetry_filters]`
**Must not pick:** `[get_interaction_filter_options, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_interaction_telemetry_filters` called.
**Notes:** "platform, app version, OS version" are the telemetry dimensions — not the interaction-list filter options.

---

### EVAL-014 — Alert scopes

**Prompt:** "What entity types can I configure alerts on in project fancode?"

**Expected tools:** `[get_alert_scopes]`
**Must not pick:** `[get_alert_metrics, get_alert_filters, list_alerts]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_alert_scopes` called.
**Notes:** "entity types" → scopes. Model must not jump straight to `get_alert_metrics` without knowing valid scopes.

---

### EVAL-015 — ANR list (default window)

**Prompt:** "Show me ANR issues for project fancode."

**Expected tools:** `[list_app_vitals_anr_issues]`
**Must not pick:** `[list_app_vitals_crash_issues, list_app_vitals_nonfatal_issues, get_interaction_error_rate]`
**Min tools:** 1 **Max tools:** 1
**Category:** single
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_app_vitals_anr_issues` called. No crash or non-fatal list tool called.
**Notes:** ANR is a distinct issue type. Tests PulseType routing (device.anr vs device.crash vs non_fatal).

---

## Category 2 — Multi-step chain

---

### EVAL-016 — Top crash investigation

**Prompt:** "Explain our top crash from last week for project fancode."

**Expected tools:** `[list_app_vitals_crash_issues, get_app_vitals_issue_summary, get_app_vitals_issue_stack_traces]`
**Must not pick:** `[list_interactions, get_interaction_error_rate, get_interaction_root_cause]`
**Min tools:** 3 **Max tools:** 6
**Category:** multi-step
**Semantic trap:** yes [P] (interactionId ≠ groupId)
**Confidence:** high

**Pass:** All three expected tools called. No interaction or metrics tools called.
**Notes:** Common misroute: using `get_interaction_root_cause` (wrong domain — App Vitals ≠ Interactions). Discovery → summary → evidence chain. `get_app_vitals_issue_screen_breakdown` is an acceptable bonus call.

---

### EVAL-017 — Interaction regression check

**Prompt:** "Is the CheckoutFlow interaction regressing in terms of latency and errors for project fancode?"

**Expected tools:** `[get_interaction_apdex_score, get_interaction_error_rate]`
**Must not pick:** `[get_interaction_root_cause, list_app_vitals_crash_issues]`
**Min tools:** 2 **Max tools:** 4
**Category:** multi-step
**Semantic trap:** yes [P] (span name not DB id)
**Confidence:** high

**Pass:** Both `get_interaction_apdex_score` and `get_interaction_error_rate` called with "CheckoutFlow" as interactionId (span name). `get_interaction_root_cause` not called.
**Notes:** Same-domain trap — both metrics tools and RCA are in the Interactions domain. The failure mode is picking the right domain but the wrong tool within it: `get_interaction_root_cause` takes a DB entity id, metrics tools take a span name. "Latency and errors" are the apdex/error-rate signals, not RCA signals.

---

### EVAL-018 — Sessions linked to a crash

**Prompt:** "Show me sessions from users who experienced the top payment crash in project fancode."

**Expected tools:** `[list_app_vitals_crash_issues, list_session_replays]`
**Must not pick:** `[list_interactions]`
**Min tools:** 2 **Max tools:** 3
**Category:** multi-step
**Semantic trap:** no
**Confidence:** high

**Pass:** Both tools called. Sessions not fetched without first identifying the crash context.

---

### EVAL-019 — Heatmap with feature gate check

**Prompt:** "Show me the heatmap for LoginScreen in project fancode, and tell me if the feature is enabled."

**Expected tools:** `[get_active_sdk_config, get_heatmap_data]`
**Must not pick:** `[list_sdk_configs, get_sdk_rules_features]`
**Min tools:** 2 **Max tools:** 2
**Category:** multi-step
**Semantic trap:** yes [P] (403 ≠ auth failure; check config first)
**Confidence:** high

**Pass:** `get_active_sdk_config` called before `get_heatmap_data`. Both called.
**Notes:** Order matters — config check gates the heatmap interpretation. `list_sdk_configs` returns all versions, not the active config.

---

### EVAL-020 — Funnel conversion details

**Prompt:** "What is the conversion data for our onboarding funnel in project fancode?"

**Expected tools:** `[list_funnels, get_funnel]`
**Must not pick:** `[list_journeys, list_interactions]`
**Min tools:** 2 **Max tools:** 2
**Category:** multi-step
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_funnels` called to find the funnel ID, then `get_funnel` called with that ID.
**Notes:** Funnels ≠ journeys. Discovery step required because funnelId is not known upfront.

---

### EVAL-021 — RCA for slowest interaction

**Prompt:** "Show me the root cause analysis for our slowest interaction in project fancode."

**Expected tools:** `[list_interactions, get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, list_app_vitals_crash_issues]`
**Min tools:** 2 **Max tools:** 3
**Category:** multi-step
**Semantic trap:** yes [P] (interactionId = DB entity id from list_interactions)
**Confidence:** high

**Pass:** `list_interactions` called first to get DB entity id, then `get_interaction_root_cause` called with that id.
**Notes:** The key trap: `interactionId` for RCA is the DB entity id from `list_interactions`, not the span name used in `get_interaction_apdex_score`.

---

### EVAL-022 — Alert configuration review

**Prompt:** "Is the latency alert configured correctly for project fancode? Show me its recent evaluation history."

**Expected tools:** `[list_alerts, get_alert_evaluation_history]`
**Must not pick:** `[get_alert_metrics, get_alert_filters]`
**Min tools:** 2 **Max tools:** 2
**Category:** multi-step
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_alerts` called to find alertId, then `get_alert_evaluation_history` called.

---

### EVAL-023 — Alert metrics for a scope

**Prompt:** "What metrics can I alert on for interactions in project fancode?"

**Expected tools:** `[get_alert_scopes, get_alert_metrics]`
**Must not pick:** `[list_alerts, get_alert_filters]`
**Min tools:** 2 **Max tools:** 2
**Category:** multi-step
**Semantic trap:** yes [P] (scope required; calling get_alert_metrics without scope → 400)
**Confidence:** high

**Pass:** `get_alert_scopes` called first to discover valid scope values, then `get_alert_metrics` called with scope.
**Notes:** Calling `get_alert_metrics` without `scope` has historically produced HTTP 400 (see 13-regression-spotchecks.md R3).

---

### EVAL-024 — Crash-free session rate

**Prompt:** "What percentage of sessions were crash-free in project fancode last week?"

**Expected tools:** `[list_app_vitals_crash_issues, get_app_vitals_user_session_totals]`
**Must not pick:** `[list_session_replays, get_interaction_error_rate]`
**Min tools:** 2 **Max tools:** 2
**Category:** multi-step
**Semantic trap:** no
**Confidence:** high

**Pass:** Both tools called (crashes as numerator, session totals as denominator).
**Notes:** `get_app_vitals_user_session_totals` is the denominator — omitting it means the model can't compute the rate correctly.

---

### EVAL-025 — Deep crash drill-down

**Prompt:** "For crash group G123 in project fancode, give me the summary, trend over time, and stack traces."

**Expected tools:** `[get_app_vitals_issue_summary, get_app_vitals_issue_trend, get_app_vitals_issue_stack_traces]`
**Must not pick:** `[list_app_vitals_crash_issues, list_session_replays]`
**Min tools:** 3 **Max tools:** 3
**Category:** multi-step
**Semantic trap:** no
**Confidence:** high

**Pass:** All three detail tools called. Group ID G123 already provided so no list call needed.
**Notes:** When groupId is already known, model should call detail tools directly without a discovery step.

---

## Category 3 — Semantic traps [P]

---

### EVAL-026 — RCA uses DB entity id, not span name [P]

**Prompt:** "Get the root cause analysis for the CheckoutFlow interaction in project fancode. The interaction entity ID is 42."

**Expected tools:** `[get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, get_interaction_error_rate, get_interaction_time]`
**Min tools:** 1 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_interaction_root_cause` called with `interactionId: "42"` (the DB entity id). No metrics tools called.
**Notes:** Same-domain trap within Interactions. Both `get_interaction_root_cause` and `get_interaction_apdex_score` belong to the same domain — the distinction is that RCA uses the DB entity id (from `list_interactions`), while metrics tools use the SpanName. The explicit "entity ID is 42" cue tests whether the model routes correctly given the hint.

---

### EVAL-027 — Apdex uses span name, not DB id [P]

**Prompt:** "What's the Apdex score for the CheckoutFlow span in project fancode over the last 24 hours?"

**Expected tools:** `[get_interaction_apdex_score]`
**Must not pick:** `[get_interaction_root_cause, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_interaction_apdex_score` called with `interactionId: "CheckoutFlow"` (span name). `get_interaction_root_cause` not called.
**Notes:** "span" is the discriminating word. The `interactionId` parameter in `get_interaction_apdex_score` is the span name string, not a numeric DB id.

---

### EVAL-028 — First/last seen crash requires eventName [P]

**Prompt:** "When was crash group G123 first and last seen in project fancode?"

**Expected tools:** `[get_app_vitals_exception_first_last_seen]`
**Must not pick:** `[get_app_vitals_issue_summary, list_app_vitals_crash_issues]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_app_vitals_exception_first_last_seen` called with `eventName: "device.crash"`. Omitting `eventName` defaults to `non_fatal` and returns empty/wrong data for crash groups — this is a grading note, not a tool-selection fail, but evaluators should flag it.
**Notes:** `eventName` omission is a parameter-level trap. Tool selection is still correct if the right tool is picked; record whether `eventName` was passed as a separate dimension.

---

### EVAL-029 — ANR first/last seen requires eventName [P]

**Prompt:** "Show me the first and last occurrence timestamps for ANR group A456 in project fancode."

**Expected tools:** `[get_app_vitals_exception_first_last_seen]`
**Must not pick:** `[list_app_vitals_anr_issues, get_app_vitals_issue_summary]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_app_vitals_exception_first_last_seen` called. Evaluator notes whether `eventName: "device.anr"` was passed.

---

### EVAL-030 — Heatmap 403 is a feature flag, not auth [P]

**Prompt:** "Why is the heatmap for HomeScreen showing an error in project fancode?"

**Expected tools:** `[get_active_sdk_config, get_heatmap_data]`
**Must not pick:** `[list_sdk_configs, get_sdk_rules_features]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_active_sdk_config` called to check heatmap feature gate; `get_heatmap_data` called. Model explains 403 as feature flag disabled, not an auth failure.
**Notes:** `get_heatmap_data` wraps errors in text JSON with `httpStatus`/`pulseError` — it does not throw. A model that treats 403 as auth failure has misread the tool description.

---

### EVAL-031 — Session listing omits startTime alone [P]

**Prompt:** "List sessions from the past week for project fancode."

**Expected tools:** `[list_session_replays]`
**Must not pick:** `[list_app_vitals_crash_issues, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** medium

**Pass:** `list_session_replays` called. Evaluator notes whether startTime+endTime were both provided or both omitted (both-or-neither rule). Providing only startTime violates the tool contract.
**Notes:** The constraint "provide both startTime and endTime together, or omit both" is a subtle trap. "Past week" with no end date is a natural phrasing — omitting both to get the default 7-day window is also valid.

---

### EVAL-032 — Interaction RCA must not use span name [P]

**Prompt:** "Run root cause analysis on the PaymentFlow interaction in project fancode. Here are the interactions: [id=7, name=PaymentFlow]."

**Expected tools:** `[get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, get_interaction_error_rate]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_interaction_root_cause` called with `interactionId: "7"` (DB entity id), not `"PaymentFlow"`.
**Notes:** Evaluator explicitly checks the `interactionId` value passed. Using "PaymentFlow" (span name) is the common operator mistake documented in 03-interactions.md TC-INT-007.

---

### EVAL-033 — alert_metrics scope required [P]

**Prompt:** "List metrics I can use for alert conditions in project fancode."

**Expected tools:** `[get_alert_scopes, get_alert_metrics]`
**Must not pick:** `[list_alerts, get_alert_filters]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_alert_scopes` called first; `get_alert_metrics` called with a scope value derived from that response.
**Notes:** Mirrors EVAL-023 but without the "interactions" context clue — the model must discover valid scopes before calling `get_alert_metrics`. Calling without scope → 400.

---

### EVAL-034 — Funnel vs journey subsystem [P]

**Prompt:** "Show me the steps in our onboarding journey in project fancode."

**Expected tools:** `[list_journeys, get_journey]`
**Must not pick:** `[list_funnels, get_funnel]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** medium

**Pass:** Journey tools called, not funnel tools. The word "journey" in the prompt is the discriminating cue.
**Notes:** Confidence medium because "journey" and "funnel" are sometimes used interchangeably in product language. If the model picks funnels and explains why, record as partial.

---

### EVAL-035 — Funnel vs interaction subsystem confusion [P]

**Prompt:** "Show me the steps in our checkout funnel for project fancode."

**Expected tools:** `[list_funnels, get_funnel]`
**Must not pick:** `[list_interactions, get_interaction_root_cause]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `list_funnels` called to discover funnelId; `get_funnel` called with that id. No interaction tools called.
**Notes:** "Checkout funnel" is a funnel-domain concept. `list_interactions` covers critical interaction monitoring — a different subsystem entirely. This is the subsystem-confusion version of Trap C.

**Parameter annotation (separate from selection score):** Note the `page` value passed to `list_funnels` — correct is `page: 1` (1-based), not `page: 0` (which is `list_interactions`' convention). Record but do not affect tool-selection score.

---

### EVAL-036 — Session email comes from JWT, not filter [P]

**Prompt:** "Show me sessions for user alice@example.com in project fancode."

**Expected tools:** `[list_session_replays]`
**Must not pick:** `[list_interactions, list_app_vitals_crash_issues]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `list_session_replays` called. Evaluator notes whether the model correctly explains that the user-email header is derived from JWT (not passed as a filter arg), and that `list_session_replays` is the right tool regardless.
**Notes:** The tool doesn't accept `userEmail` as a parameter. Model should use the tool and note the JWT-email behavior.

---

### EVAL-037 — Non-fatal first/last seen omits eventName (default behavior) [P]

**Prompt:** "When were non-fatal issue groups NF1 and NF2 first seen in project fancode?"

**Expected tools:** `[get_app_vitals_exception_first_last_seen]`
**Must not pick:** `[list_app_vitals_nonfatal_issues, get_app_vitals_issue_summary]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_app_vitals_exception_first_last_seen` called with `groupIds: ["NF1", "NF2"]`. Omitting `eventName` is correct here (defaults to non_fatal). Evaluator confirms no crash-type tools were called.
**Notes:** Inverse of EVAL-028 — omitting `eventName` IS correct for non-fatal. Tests that the model knows the default.

---

## Category 3b — Semantic traps (untelegraphed) [P]

These are companion cases to EVAL-026, EVAL-027, EVAL-032. The telegraphed versions include explicit cues ("entity ID is 42", "span", "[id=7, name=...]") that guide the model. These untelegraphed versions remove those cues to test whether the model can route correctly from the tool descriptions alone.

**Evaluator note:** Compare pass rate between telegraphed (EVAL-026/027/032) and untelegraphed (EVAL-056/057/058) versions. A large gap indicates the tool description is too ambiguous for cold routing.

---

### EVAL-056 — RCA routing without explicit id hint (untelegraphed) [P]

**Prompt:** "Get the root cause analysis for the CheckoutFlow interaction in project fancode."

**Expected tools:** `[list_interactions, get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, get_interaction_error_rate]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** medium

**Pass:** `list_interactions` called first to discover the DB entity id for CheckoutFlow; then `get_interaction_root_cause` called with that id. No metrics tools called.
**Notes:** No "entity ID" hint in the prompt. Model must infer from `get_interaction_root_cause` description that `interactionId` is a DB entity id (from `list_interactions`), not the span name "CheckoutFlow". Failure here (e.g. calling `get_interaction_apdex_score`) is a description gap signal.

---

### EVAL-057 — Apdex routing without "span" cue (untelegraphed) [P]

**Prompt:** "What's the Apdex score for CheckoutFlow in project fancode over the last 24 hours?"

**Expected tools:** `[get_interaction_apdex_score]`
**Must not pick:** `[get_interaction_root_cause, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** medium

**Pass:** `get_interaction_apdex_score` called with `interactionId: "CheckoutFlow"`. `get_interaction_root_cause` not called.
**Notes:** No "span" cue. Model must know from `get_interaction_apdex_score` description that `interactionId` here is a span name string. Compare to EVAL-027 (telegraphed) — if failure rate is much higher here, the description needs a stronger disambiguation.

---

### EVAL-058 — RCA id selection without structured hint (untelegraphed) [P]

**Prompt:** "Run root cause analysis on the PaymentFlow interaction in project fancode."

**Expected tools:** `[list_interactions, get_interaction_root_cause]`
**Must not pick:** `[get_interaction_apdex_score, get_interaction_error_rate]`
**Min tools:** 2 **Max tools:** 2
**Category:** semantic-trap
**Semantic trap:** yes [P]
**Confidence:** medium

**Pass:** `list_interactions` called to find PaymentFlow's DB entity id; `get_interaction_root_cause` called with that id (not with "PaymentFlow" string).
**Notes:** No "[id=7, name=...]" structured data in prompt. Compare to EVAL-032 (telegraphed). A model passing EVAL-032 but failing EVAL-058 is using the structured hint as a shortcut, not the tool description.

---

## Category 4 — Distractor traps

---

### EVAL-038 — Crash trends ≠ interactions

**Prompt:** "Show me crash trends for project fancode."

**Expected tools:** `[list_app_vitals_crash_issues]`
**Must not pick:** `[list_interactions, get_interaction_error_rate, get_interaction_categorization]`
**Min tools:** 1 **Max tools:** 2
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_app_vitals_crash_issues` called. No interaction or metrics tools called.
**Notes:** "Trends" might tempt `get_interaction_time` or `get_interaction_error_rate`. Crash data lives in App Vitals, not interaction metrics.

---

### EVAL-039 — List alerts ≠ alert history

**Prompt:** "List all alerts configured for project fancode."

**Expected tools:** `[list_alerts]`
**Must not pick:** `[get_alert_evaluation_history, get_alert_filters, get_alert_metrics]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_alerts` called. `get_alert_evaluation_history` (per-alert detail, not list) not called.

---

### EVAL-040 — Search events ≠ list event definitions

**Prompt:** "Search for events with 'purchase' in their name in project fancode."

**Expected tools:** `[search_events]`
**Must not pick:** `[get_event_definition]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** medium

**Pass:** `search_events` called with `searchString: "purchase"`. `list_event_definitions` with `search` param is also acceptable (score noise-call 0.9).
**Notes:** Both `search_events` and `list_event_definitions` have search capability. `search_events` is the autocomplete-style tool; `list_event_definitions` has broader pagination. Confidence medium — both are defensible. `get_event_definition` (single-item lookup by id) is the hard wrong answer.

---

### EVAL-041 — Error rate needs a specific interaction

**Prompt:** "What is the overall error rate for project fancode?"

**Expected tools:** `[get_interaction_error_rate]`
**Must not pick:** `[list_app_vitals_nonfatal_issues, list_app_vitals_crash_issues]`
**Min tools:** 1 **Max tools:** 2
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** medium

**Pass:** `get_interaction_error_rate` called. Evaluator notes the model should ask for or infer a specific interaction span name — "overall" error rate requires choosing a span.
**Notes:** Confidence medium — "overall error rate" is ambiguous. A clarifying question is also acceptable (score 1.0 per rubric). Non-fatal/crash lists are wrong-subsystem answers.

---

### EVAL-042 — Sessions with crashes ≠ crash list alone

**Prompt:** "Show me sessions from users who had crashes in project fancode."

**Expected tools:** `[list_session_replays]`
**Must not pick:** `[list_interactions]`
**Min tools:** 1 **Max tools:** 2
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** medium

**Pass:** `list_session_replays` called. Model understands the goal is session data.
**Notes:** `list_app_vitals_crash_issues` is a reasonable discovery step (score noise-call 0.9 if also called — not a must_not_pick). The deliverable is sessions, not the crash group list. `list_interactions` (wrong subsystem) is the hard wrong answer.

---

### EVAL-043 — Journey ≠ funnel for "user path"

**Prompt:** "Show me the user path through the checkout flow in project fancode."

**Expected tools:** `[list_journeys, get_journey]`
**Must not pick:** `[list_funnels, get_funnel]`
**Min tools:** 2 **Max tools:** 2
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** medium

**Pass:** Journey tools called. "User path" aligns with journeys concept.
**Notes:** Confidence medium — "checkout flow" could map to either funnels or journeys depending on how the project is set up. If model picks funnels with reasoning, record as discussion item.

---

### EVAL-044 — Active SDK config ≠ config list

**Prompt:** "What is the currently active SDK configuration for project fancode?"

**Expected tools:** `[get_active_sdk_config]`
**Must not pick:** `[list_sdk_configs, get_sdk_config]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_active_sdk_config` called.
**Notes:** "currently active" is the discriminating phrase. `list_sdk_configs` returns all versions; `get_sdk_config` needs a version number.

---

### EVAL-045 — Non-fatal ≠ crash or ANR

**Prompt:** "Show me non-fatal errors in project fancode."

**Expected tools:** `[list_app_vitals_nonfatal_issues]`
**Must not pick:** `[list_app_vitals_crash_issues, list_app_vitals_anr_issues, get_interaction_error_rate]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_app_vitals_nonfatal_issues` called. No crash or ANR list called.

---

### EVAL-046 — Alert history for specific alert ≠ list alerts

**Prompt:** "Show me the evaluation history for alert ID alert-xyz in project fancode."

**Expected tools:** `[get_alert_evaluation_history]`
**Must not pick:** `[list_alerts, get_alert_filters]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_alert_evaluation_history` called with `alertId: "alert-xyz"`. Alert ID already provided so no list step needed.

---

### EVAL-047 — SDK config version lookup ≠ active config

**Prompt:** "Show me SDK config version 3 for project fancode."

**Expected tools:** `[get_sdk_config]`
**Must not pick:** `[get_active_sdk_config, list_sdk_configs]`
**Min tools:** 1 **Max tools:** 1
**Category:** distractor-trap
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_sdk_config` called with `version: 3`. "Version 3" is explicit — no list or active-config call needed.

---

## Category 5 — Disambiguation

---

### EVAL-048 — Performance data = apdex, not RCA

**Prompt:** "Get performance metrics for the HomeScreen span in project fancode."

**Expected tools:** `[get_interaction_apdex_score]`
**Must not pick:** `[get_interaction_root_cause]`
**Min tools:** 1 **Max tools:** 3
**Category:** disambiguation
**Semantic trap:** yes [P]
**Confidence:** high

**Pass:** `get_interaction_apdex_score` called with `interactionId: "HomeScreen"`. `get_interaction_root_cause` not called.
**Notes:** "performance metrics" + "span" → apdex/error-rate subsystem. "span" explicitly rules out the RCA DB-entity-id path.

---

### EVAL-049 — Crash detail (groupId known) = summary, not list

**Prompt:** "Show me detailed information about crash group G789 in project fancode."

**Expected tools:** `[get_app_vitals_issue_summary]`
**Must not pick:** `[list_app_vitals_crash_issues]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_app_vitals_issue_summary` called. `list_app_vitals_crash_issues` not called (groupId already known).
**Notes:** When a group ID is provided, the list tool is redundant. Tests disambiguation between discovery and detail.

---

### EVAL-050 — Trend over time ≠ screen breakdown

**Prompt:** "How has crash group G789 been trending over time in project fancode?"

**Expected tools:** `[get_app_vitals_issue_trend]`
**Must not pick:** `[get_app_vitals_issue_screen_breakdown, get_app_vitals_issue_summary]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_app_vitals_issue_trend` called. "Over time" → time-bucketed trend, not screen breakdown.

---

### EVAL-051 — Screen breakdown ≠ trend

**Prompt:** "Which screens are most affected by crash group G789 in project fancode?"

**Expected tools:** `[get_app_vitals_issue_screen_breakdown]`
**Must not pick:** `[get_app_vitals_issue_trend, get_app_vitals_issue_stack_traces]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_app_vitals_issue_screen_breakdown` called. "Which screens" → breakdown by screen, not time trend.

---

### EVAL-052 — List SDK configs ≠ get active config

**Prompt:** "What SDK configuration versions are available for project fancode?"

**Expected tools:** `[list_sdk_configs]`
**Must not pick:** `[get_active_sdk_config, get_sdk_config]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_sdk_configs` called. "Versions available" → list, not active-only or specific-version.

---

### EVAL-053 — Filter options vs telemetry filters

**Prompt:** "What status values can I use to filter the interaction list in project fancode?"

**Expected tools:** `[get_interaction_filter_options]`
**Must not pick:** `[get_interaction_telemetry_filters, list_interactions]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `get_interaction_filter_options` called.
**Notes:** "status values for the interaction list" → filter options (UI list filter). `get_interaction_telemetry_filters` returns platform/appVersion/OS dimensions (dashboard filters), not list-status values.

---

### EVAL-054 — List funnels with tag filter ≠ get funnel tags

**Prompt:** "Find funnels tagged with 'checkout' in project fancode."

**Expected tools:** `[list_funnels]`
**Must not pick:** `[get_funnel_tags, get_funnel]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_funnels` called with `tags: ["checkout"]`. `get_funnel_tags` (which lists all tags, not filters by them) not called.
**Notes:** `get_funnel_tags` returns the tag vocabulary; `list_funnels` accepts tags as a filter. Common confusion.

---

### EVAL-055 — Interaction list (all) vs suggested interactions

**Prompt:** "List all interactions being monitored for project fancode."

**Expected tools:** `[list_interactions]`
**Must not pick:** `[list_suggested_interactions, get_interaction_filter_options]`
**Min tools:** 1 **Max tools:** 1
**Category:** disambiguation
**Semantic trap:** no
**Confidence:** high

**Pass:** `list_interactions` called. `list_suggested_interactions` (AI suggestions for new ones, not the existing monitored list) not called.
**Notes:** "being monitored" = existing critical interactions. "suggested" = AI-recommended ones not yet added.

---

## Coverage Summary

| Tool | EVAL cases |
|------|-----------|
| `list_projects` | EVAL-001 |
| `get_project` | EVAL-002 |
| `list_project_members` | EVAL-003 |
| `list_interactions` | EVAL-021, EVAL-032, EVAL-055, EVAL-056, EVAL-058 |
| `list_suggested_interactions` | EVAL-004, EVAL-055 |
| `get_interaction_filter_options` | EVAL-005, EVAL-053 |
| `get_interaction_telemetry_filters` | EVAL-013 |
| `get_interaction_root_cause` | EVAL-021, EVAL-026, EVAL-032, EVAL-056, EVAL-058 |
| `list_event_definitions` | EVAL-040 |
| `get_event_definition` | EVAL-040 |
| `list_event_categories` | EVAL-009 |
| `search_events` | EVAL-040 |
| `get_interaction_apdex_score` | EVAL-017, EVAL-027, EVAL-048, EVAL-057 |
| `get_interaction_error_rate` | EVAL-017, EVAL-041 |
| `get_interaction_time` | EVAL-017 |
| `get_interaction_categorization` | EVAL-017 |
| `list_session_replays` | EVAL-018, EVAL-031, EVAL-036, EVAL-042 |
| `list_funnels` | EVAL-020, EVAL-035, EVAL-054 |
| `get_funnel` | EVAL-020, EVAL-043 |
| `get_funnel_tags` | EVAL-010, EVAL-054 |
| `get_funnel_events` | EVAL-011 |
| `get_funnel_filters` | EVAL-012 |
| `list_journeys` | EVAL-034, EVAL-043 |
| `get_journey` | EVAL-034, EVAL-043 |
| `list_alerts` | EVAL-022, EVAL-039 |
| `get_alert_evaluation_history` | EVAL-022, EVAL-046 |
| `get_alert_filters` | EVAL-039 |
| `get_alert_scopes` | EVAL-014, EVAL-023, EVAL-033 |
| `get_alert_metrics` | EVAL-023, EVAL-033 |
| `get_alert_severities` | EVAL-006 |
| `list_alert_notification_channels` | EVAL-007 |
| `get_heatmap_data` | EVAL-019, EVAL-030 |
| `get_active_sdk_config` | EVAL-019, EVAL-030, EVAL-044 |
| `list_sdk_configs` | EVAL-047, EVAL-052 |
| `get_sdk_config` | EVAL-047 |
| `get_sdk_rules_features` | EVAL-008 |
| `list_app_vitals_crash_issues` | EVAL-016, EVAL-018, EVAL-024, EVAL-038 |
| `list_app_vitals_anr_issues` | EVAL-015, EVAL-029 |
| `list_app_vitals_nonfatal_issues` | EVAL-037, EVAL-045 |
| `get_app_vitals_user_session_totals` | EVAL-024 |
| `get_app_vitals_issue_summary` | EVAL-025, EVAL-049 |
| `get_app_vitals_issue_trend` | EVAL-025, EVAL-050 |
| `get_app_vitals_issue_stack_traces` | EVAL-016, EVAL-025 |
| `get_app_vitals_issue_screen_breakdown` | EVAL-051 |
| `get_app_vitals_exception_first_last_seen` | EVAL-028, EVAL-029, EVAL-037 |
