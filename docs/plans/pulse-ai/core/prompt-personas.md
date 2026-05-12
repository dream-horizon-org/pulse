# pulse-ai / core / prompt-personas

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md).

## Purpose

Document the prompt construction for every persona shipped today. Prompts are
**callable** in ADK terms (`instruction=build_system_prompt`) so dynamic
context — current UTC, prior agent output, segment data — can be injected at
request time rather than baked into a string constant.

## Source location

- `pulse_ai/agents/em/prompts.py` — `build_system_prompt(ctx)`.
- `pulse_ai/agents/rca/prompts.py` — `build_rca_prompt(ctx)`.
- `pulse_ai/agents/report/prompts.py` — `build_report_prompt(ctx)`.
- `pulse_ai/agents/screen_rca/prompts.py` — screen-RCA persona prompt.

## Public surface

Each module exports one builder:

| Function | Returned to ADK as | Reads from `ctx` |
|---|---|---|
| `build_system_prompt(ctx)` | EM agent system prompt | — (just `datetime.utcnow()`) |
| `build_rca_prompt(ctx)` | RCA agent system prompt | — |
| `build_report_prompt(ctx)` | Report agent system prompt | `ctx.state["engineering_manager_result"]` |
| `build_screen_rca_prompt(ctx)` | Screen-RCA agent system prompt | — |

## Internal design

### EM persona

`agents/em/prompts.py` builds the prompt with:

- Injected `Current UTC time: {now}` (ISO `YYYY-MM-DDTHH:MM:SSZ`) so the LLM
  can resolve relative time ranges like "last Tuesday to Wednesday"
  (Mechanism B from the time-range design).
- Sections: role definition (Pulse Engineering Manager Agent), interaction
  definition (start event T0 + end event T1 → duration), capabilities, and
  behaviour rules:
  - **TIME RANGE:** default to last 24 h when unspecified; always echo the
    range used.
  - **CLARIFICATION OVER ASSUMPTION:** never silently pick an interaction
    from a list — ask the user.
  - **RICH RESPONSES:** for a single named interaction, call 2–3 tools
    together; for "all metrics" use
    `query_interaction_metrics(metric_type="composite")`.
  - Comparison rules, parameter-hallucination guardrails.

### RCA persona

`agents/rca/prompts.py`. Pure reasoning agent (no tools). Highlights:

- Takes pre-computed segment data as the user message.
- Must output JSON matching `RcaStructuredReportV1`.
- Each segment input carries `exampleSessionIds` — the agent must copy them
  verbatim into `affected_sessions` in the output.
- Tasks: identify anomalies, correlations, root causes across segment
  dimensions (Platform, Region, Device Model, OS Version, …).

### Report persona

`agents/report/prompts.py` reads `ctx.state["engineering_manager_result"]` and
splices it into the system prompt under "Analysis Results". If state is empty
the prompt instructs the agent to tell the user no analysis was performed.

Visualization tools:

- `create_chart` — for trends and distributions.
- `create_table` — for tabular comparisons.

Both tools live in `agents/report/tools/` and follow the standard ADK tool
signature.

### Screen-RCA persona

`agents/screen_rca/agent.py` + `prompts.py` produce a `ScreenRcaNarrativeV1`
report focused on per-screen exception clustering.

## Dependencies

- `datetime.datetime` (UTC stamping for EM prompt).
- `pulse_ai.schemas.rca_structured_v1.RcaStructuredReportV1` (RCA output schema).
- `pulse_ai.schemas.screen_rca_narrative_v1.ScreenRcaNarrativeV1` (Screen-RCA).

## Data contracts

- `ctx` is an `ADK ReadonlyContext`. The EM and RCA builders ignore it
  (signature requires it). The Report builder accesses `ctx.state`
  defensively (`getattr` + `try/except`) because state may not exist on every
  run.
- The persona names are pulled from `pulse_ai/constants.py`:
  `EM_AGENT_NAME`, `RCA_AGENT_NAME`, `REPORT_AGENT_NAME`,
  `SCREEN_RCA_NARRATIVE_AGENT_NAME`.

## Tests

- `tests/test_interaction_templates.py` — EM template assembly.
- `tests/test_rca_runner_prompt_order.py` — prompt ordering before RCA runner.
- `tests/test_templates_base.py` — shared template helpers (`TIME_RANGE_DOC`).

## History / decisions

- Mechanism B (inject current time) beat baking a static date because the LLM
  was previously mis-anchoring relative time ranges.
- The Report agent reads its predecessor's output by key (`engineering_manager_result`)
  rather than via tool arguments; this keeps the sequential pipeline contract
  loose and lets the EM agent emit free-form text.
- README persona table (Product Analytics, Designer, Customer Success,
  Business Leaders, EM) is the longer-term plan; only EM + RCA + Report +
  Screen-RCA exist today.

## Rebuild recipe

```python
def build_system_prompt(ctx=None) -> str:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return f"""You are the Pulse Engineering Manager Agent. … Current UTC time: {now} …"""

def build_report_prompt(ctx=None) -> str:
    analysis = (ctx and getattr(ctx, 'state', {}).get("engineering_manager_result")) \
               or "No analysis data available."
    return f"""You are the Report Agent … ## Analysis Results\n{analysis}\n…"""
```
