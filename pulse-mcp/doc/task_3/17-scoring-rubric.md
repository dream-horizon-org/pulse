# Scoring Rubric — LLM Tool-Selection Eval

This rubric applies to `16-eval-nl-prompts.md`. It grades **tool selection only** — argument correctness is handled by the integration tests in files 01–13.

---

## Per-Case Scoring

| Result | Condition | Score |
|--------|-----------|-------|
| Full pass | All expected tools called; no must_not_pick tool called | **1.0** |
| Noise call | All expected tools called + one or more extra tools not in must_not_pick | **0.9** (one penalty regardless of how many noise tools) |
| Clarifying question | Model asks for genuinely missing required info before calling tools | **1.0** only when the prompt is truly ambiguous (e.g. "show me data" with no project/entity context). Do NOT score 1.0 if the prompt provides sufficient context — an unnecessary clarification is over-caution, not correctness. |
| Fail — wrong tool | Any must_not_pick tool called (even if expected tools also called) | **0.0** |
| Fail — missed tool | At least one expected tool not called | **0.0** |

**Note on `must_not_pick`:** Lists only the 2–4 plausible-but-wrong alternatives per case. The absence of every other registry tool is implied.

**Note on repeated tool calls (pagination):** A tool called twice (e.g. `list_session_replays` page 1 then page 2 via cursor) counts as **2 calls** and both count toward `Min/Max tools`. SCN-F expects `[list_session_replays, list_session_replays]` — two distinct calls.

---

## Per-Case Precision and Recall

For multi-step cases where partial credit is meaningful, compute alongside the binary score above:

```
Precision (per case) = |called ∩ expected| / |called|
Recall    (per case) = |called ∩ expected| / |expected|
F1        (per case) = 2 × (Precision × Recall) / (Precision + Recall)
```

Aggregate by averaging across all cases in a category or the full corpus.

---

## Aggregate Headline Metrics

Report these after running the full corpus:

| Metric | Definition |
|--------|-----------|
| **Overall pass rate** | Cases scoring 1.0 or 0.9 / total cases |
| **Hard pass rate** | Cases scoring exactly 1.0 / total cases |
| **Semantic trap accuracy** | Pass rate on `semantic-trap` category cases only |
| **Distractor resistance** | 1 − (must_not_pick violation rate on `distractor-trap` cases) |
| **Multi-step recall** | Average per-case recall across `multi-step` cases |
| **Corpus F1** | Average F1 across all cases |

---

## Confidence Tags

Each eval case carries a `**Confidence:** high | medium` tag on its ground truth.

- **`high`** — the expected tool set is unambiguous; failures are clear grading failures.
- **`medium`** — the correct tool is defensible but debatable (e.g. "journey" vs "funnel"). Failures on `medium` cases are **discussion items**, not hard failures. They often indicate tool description improvement opportunities.

Before using this corpus as a hard benchmark, peer-review all `medium` cases and either promote to `high` or document the ambiguity explicitly.

---

## Parameter Annotations (separate from tool-selection score)

The following cases carry a parameter-level trap on top of tool-selection. Record a **parameter annotation** alongside the binary score — it does not change the tool-selection score but feeds a separate quality dimension.

| EVAL | Parameter being graded | Correct value | Wrong value |
|------|------------------------|---------------|-------------|
| EVAL-026 | `interactionId` in `get_interaction_root_cause` | DB entity id (e.g. "42") | Span name (e.g. "CheckoutFlow") |
| EVAL-028 | `eventName` in `get_app_vitals_exception_first_last_seen` | "device.crash" | omitted (defaults to non_fatal) |
| EVAL-029 | `eventName` in `get_app_vitals_exception_first_last_seen` | "device.anr" | omitted |
| EVAL-031 | `startTime`/`endTime` in `list_session_replays` | both provided or both omitted | only `startTime` provided |
| EVAL-032 | `interactionId` in `get_interaction_root_cause` | DB entity id (e.g. "7") | Span name (e.g. "PaymentFlow") |
| EVAL-035 | `page` in `list_funnels` | 1 (1-based) | 0 (list_interactions convention) |
| EVAL-037 | `eventName` in `get_app_vitals_exception_first_last_seen` | omitted (correct for non_fatal) | "device.crash" or "device.anr" |
| EVAL-056 | `interactionId` in `get_interaction_root_cause` | DB entity id from `list_interactions` response | Span name "CheckoutFlow" |
| EVAL-058 | `interactionId` in `get_interaction_root_cause` | DB entity id from `list_interactions` response | Span name "PaymentFlow" |
| SCN-B | `interactionId` in `get_interaction_apdex_score` / `get_interaction_error_rate` | Span name "CheckoutFlow" | Numeric DB id |
| SCN-C | `interactionId` in `get_interaction_root_cause` | DB entity id from `list_interactions` | Span name "PaymentFlow" |

Record parameter annotations in the format:
```
Tool selection: PASS (1.0)
Parameter annotation [EVAL-028]: eventName = "device.crash" ✓
```

---

## Failure Attribution

After running the corpus, group failures by root cause:

| Failure pattern | Likely root cause |
|-----------------|------------------|
| Model picks wrong domain (e.g. interactions for crash issues) | Tool description doesn't clearly state the domain boundary |
| Model confuses list vs detail tool | Description doesn't differentiate "list all" vs "get one by ID" |
| **Model picks right domain, wrong tool within Interactions** | The hardest failure class — metrics tools and RCA are in the same domain but `interactionId` means different things |
| Model calls `get_interaction_root_cause` with span name | RCA description just says "Interaction ID" — no warning that it's the DB entity id from `list_interactions`, not the span name |
| Model calls `get_interaction_apdex_score` with DB entity id | Metrics description says "not necessarily the numeric DB id" — weak phrasing |
| Model omits `get_alert_scopes` before `get_alert_metrics` | `get_alert_metrics` description doesn't make scope a prerequisite |
| Model treats heatmap 403 as auth failure | Heatmap tool description 403-semantics section missed |
| Model picks `list_suggested_interactions` instead of `list_interactions` | "AI-suggested" vs "monitored" distinction unclear |

Tools with ≥ 30% failure rate across their EVAL cases are candidates for description rewrites in `src/tools/*.ts`.

**Rename decision:** Renaming `get_interaction_apdex_score` → `get_interaction_apdex_score` and `get_interaction_error_rate` → `get_interaction_error_rate` is a breaking API change. Defer until after baseline eval run — if the same-domain failure rate is high, a description fix on the existing names is sufficient. A rename is only warranted if the eval shows the name itself (not the description) is causing routing failures.

---

## Running the Eval (manual)

1. Open an LLM session with the pulse-mcp server connected (Cursor, Claude Desktop, or Claude Code with MCP configured).
2. For each case in `16-eval-nl-prompts.md`, send the **Prompt** verbatim.
3. Record which tools were called (tool names only, not arguments, for the selection score).
4. For semantic-trap cases, also record the key argument values for the parameter annotation.
5. Score each case using the table above.
6. Compute aggregate metrics.
7. Flag all `medium`-confidence cases that failed for peer review.

---

## Threshold for Corpus Acceptance

The corpus is considered **eval-ready** when:

- All tools in `00-matrix-tool-inventory.md` appear in at least one `expected_tools` list
- All `[P]`-tagged tools have at least one `semantic-trap` case
- All 6 SCN scenarios in `14-multi-step-agent-scenarios.md` have `expected_tools:` blocks
- Ground truth has been peer-reviewed for all `medium`-confidence cases

Current status against these criteria is tracked in the `Eval coverage` column in `00-matrix-tool-inventory.md`.
