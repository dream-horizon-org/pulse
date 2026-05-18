# Deferred Eval Cases

These cases are scoped but not yet implemented. Each is blocked by a dependency noted below.

## Blocking categories

**HTTP mock layer** — EM analytics/config tools make real HTTP calls to the Pulse backend. Eval runs would require either (a) a seeded dev stack, or (b) a mock eval agent that replaces tool implementations with fixtures. Neither exists yet.

**Phase 2 custom metrics** — ADK 1.26 supports custom Python metric functions. These would allow precise structural assertions (e.g., exact session ID copy, all 10 metric IDs present, attribution count faithfulness) beyond what ROUGE-L provides. Implementing requires a Python eval runner script instead of the `adk eval` CLI.

**LLM-as-judge metric** — ADK's `final_response_match_v2` / `rubric_based_final_response_quality_v1` metrics provide rubric-based scoring. Requires verifying availability in ADK 1.26 and confirming cost implications.

**Integration harness** — `deploy/scripts/rca-audit.py` covers full-pipeline checks (server segmentation → LLM) against a seeded backend. Cases in that category belong in `rca-audit.py`, not ADK evals, since they depend on real ClickHouse seed data.

---

## Implemented (not deferred)

| Case ID | Where | What it tests |
|---------|-------|---------------|
| `rca_two_segments_degraded` | `rca.evalset.json` | Session ID copy, all 10 metrics, null attribution |
| `rca_without_error_attribution` | `rca.evalset.json` | No attribution → both fields null, no invented drill data |
| `rca_with_error_attribution` | `rca.evalset.json` | Exactly 3 signals in anr→non_fatal→api order, faithful numeric copy |
| `rca_everything_good` | `rca.evalset.json` | `everythingGood: true` → 0 segments, empty recommendations, no fabrication |
| `rca_no_fabrication_single_segment` | `rca.evalset.json` | 1 input segment → 1 output segment, agent must not hallucinate a 2nd |
| `rca_rank_three_segments` | `rca.evalset.json` | 3 segments with clear severity gap → worst APDEX must be rank 1 |
| `em_calculate_rate` (A1) | `em.evalset.json` | Rule 8a — always use calculate tool, never mental math |
| `em_write_confirmation` | `em.evalset.json` | Rule 12 — no write ops without confirmation; agent correctly reports no write capability |
| `calculate_sum` (smoke) | `em.evalset.json` | Baseline calculate tool smoke |

---

## EM Agent — HTTP-dependent cases

| Case ID | Rule tested | Expected trajectory | Blocking dependency |
|---------|-------------|---------------------|---------------------|
| `em_health_top_ten` (B3) | Rule 3 — vague query → top-10 health, then ask which to drill | `query_interaction_health(top_n=10)` | HTTP mock layer |
| `em_interaction_detail` (B2) | Rule 5 — no hallucinated interaction names | `query_interactions(scope="detail", interaction_name=<known name>)` | HTTP mock layer + seeded interaction |
| `em_specific_interaction_threshold_compare` (Rule 9) | Rule 9 — querying specific interaction must call BOTH metrics AND detail in parallel | `query_interaction_metrics` + `query_interactions(scope="detail")` | HTTP mock layer |
| `em_breakdown_platform` (B6 / Rule 7a) | Rule 7a — platform comparison uses `breakdown_interaction`, NOT two filtered health calls | `breakdown_interaction(dimension="platform")` | HTTP mock layer |
| `em_compare_two_interactions` (B7) | Rule 7 — per-interaction tools ×2 + synthesis | `query_interaction_health` ×2 or metrics ×2 | HTTP mock layer |
| `em_sessions_by_event` (B9) | Session query by crash/error type | `query_interaction_sessions(event_type="crash")` | HTTP mock layer |
| `em_alert_list` (B8) | Alert list / evaluation history | `query_alerts(scope="list")` | HTTP mock layer |
| `em_no_hallucinate_interaction` (C1 / Rule 5) | Must not invent interaction name for vague query | `query_interaction_health` (top-10), response asks which interaction | HTTP mock layer |
| `em_empty_data_time_range` (Rule 11) | Empty response → suggest broader time range | Any tool call returning empty data | HTTP mock layer returning empty response |

---

## EM Agent — Multi-turn / guardrail cases

| Case ID | Rule tested | Notes | Blocking dependency |
|---------|-------------|-------|---------------------|
| `em_multi_turn_context` (Rule 10) | Carry context across turns — follow-up should not re-ask interaction name | Requires multi-turn evalset format + stable fixture | HTTP mock layer + multi-turn ADK evalset |
| `em_time_range_in_answer` (C2 / Rule 2) | Response always states the time period covered | Response_match assertion on "last 24 hours" language | HTTP mock layer (tool call needed to generate a real response) |

---

## RCA Agent — Precision assertion cases

| Case ID | What it checks | Notes | Blocking dependency |
|---------|----------------|-------|---------------------|
| RCA custom metric: all-metrics-present | All 10 `metric_id` values from input appear in each output segment | ROUGE-L approximates this; exact check needs custom metric | Phase 2 custom metrics |
| RCA custom metric: attribution faithfulness (D6 precision) | `occurrences`, `rr`, and `relatedAttributions` count unchanged from input | Current D6 uses ROUGE which catches obvious changes but not subtle numeric drift | Phase 2 custom metrics |
| RCA custom metric: no-fabrication segment count | `len(output.segments) == len(input.segments)` when input has 1 segment | `rca_no_fabrication_single_segment` approximates this via ROUGE; exact assertion needs custom metric | Phase 2 custom metrics |
| `rca_volume_tiebreak` (D2) | When two segments have identical severity, higher volume ranks first | `rca_rank_three_segments` tests clear-gap ranking; volume tie-break needs subtler fixture + LLM judge | LLM-as-judge metric |
| `rca_no_data` (D4) | `noDataAvailable: true` → honest "no data" outcome, no invented segments | Similar to `rca_everything_good` but different flag — defer until `noDataAvailable` path is exercised in production | LLM-as-judge metric |
| `rca_mixed_dimensions` (D5) | Segments with different dimension combos → no invented hierarchy | Narrative must not impose hierarchical structure on flat segments | LLM-as-judge metric |

---

## RCA integration cases — stay in rca-audit.py

These cases were identified by reviewing `deploy/scripts/rca-audit.py` (branch `fix/rca-minor-fixes`). They require the full pipeline (server segmentation → LLM) with seeded ClickHouse data and cannot be replicated as offline ADK evals.

| Case | Interaction | What rca-audit.py checks | Why not ADK eval |
|------|-------------|--------------------------|------------------|
| Device keyword presence | `app_launch`, `checkout_start`, `image_gallery_load` | SM-A135F, Redmi Note 12, OnePlus appear in output segments | Keywords come from seeded ClickHouse — not reproducible offline |
| Forbidden keyword guard | `home_feed_load`, `product_search`, `order_confirmation` | WiFi+4.3.0, iOS+4.3.0, Pixel+14 must NOT appear | These are filtered by the server (direction filter, eligibility gate) — not LLM responsibility |
| Combined-signal floor | All interactions | S = \|Δerror_rate\| + \|Δpoor_user_pct\| ≥ 15.0 per segment | Server-side gate; LLM receives already-filtered segments |
| Compound keyword matching | `coupon_apply`, `order_tracking` | All 4 dimensions in compound segment title (android+12+Redmi+Jio) | Seeded compound segments required |
| `notifications_open` full pipeline | Healthy interaction | `everything_good=true`, no segments, empty recommendations, confirmed end-to-end | ADK eval `rca_everything_good` tests LLM contract; audit tests server pre-analysis gate too |
| `deeplink_open` ambiguous outcome | Either outcome acceptable | iOS 16 + Vi borderline volume — either `everything_good` or 1 segment | Only meaningful with real volume data from seeded backend |

---

## Implementation path for unblocking HTTP cases

Two viable options:

**Option A — Mock eval agent**: Create a parallel `adk_eval_em_mock_app/` that wraps `em_agent` but replaces HTTP tool functions with fixture-returning stubs. Tool names and signatures stay identical; HTTP calls are replaced by `return fixture_data[tool_name]`. Low infrastructure cost, closest to real agent behavior.

**Option B — Seeded dev stack**: Run evals against a local Pulse backend seeded with deterministic fixture data. Requires Docker compose + seed script. Higher fidelity but slower CI cycle.

Recommendation: **Option A first** (mock eval agent), then **Option B** for integration-level evals.
