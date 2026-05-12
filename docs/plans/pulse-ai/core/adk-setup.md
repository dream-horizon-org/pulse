# pulse-ai / core / adk-setup

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md).

## Purpose

Document how Pulse AI wires Google ADK: which agent types are used, how tools
are declared, how outputs are typed, and which model id ADK loads.

## Source location

- `pulse_ai/agent.py` — `root_agent = SequentialAgent(...)`.
- `pulse_ai/agents/__init__.py` — re-exports of `em_agent`, `report_agent`,
  `rca_agent`.
- `pulse_ai/agents/em/agent.py`, `agents/rca/agent.py`,
  `agents/report/agent.py`, `agents/screen_rca/agent.py` — concrete instances.
- `pulse_ai/constants.py` — `AGENT_MODEL`, agent name constants.

## Public surface (ADK building blocks)

ADK pieces actually imported by the codebase:

| Import | Used by | Notes |
|---|---|---|
| `google.adk.agents.sequential_agent.SequentialAgent` | `agent.py` | EM → Report pipeline. |
| `google.adk.agents.llm_agent.Agent` | `agents/em/agent.py` | Tool-using LLM agent. |
| `google.adk.agents.llm_agent.LlmAgent` | `agents/rca`, `report`, `screen_rca` | LLM agent with optional `output_schema`. |
| `google.adk.runners.Runner` | `server/app.py` | Wraps an agent + session service for HTTP. |
| `google.adk.sessions.{InMemorySessionService, DatabaseSessionService}` | `server/app.py` | Session backends. |
| `google.adk.tools.ToolContext` | every tool function | Passed by ADK at call time, used to read auth state. |

`ParallelAgent` and `LoopAgent` are part of the ADK contract Pulse AI is built
against (mentioned in `CLAUDE.md`), but the current shipped pipeline only uses
`SequentialAgent`.

## Internal design

### Tool contract

Every Pulse AI tool is a plain async Python function with type-annotated
parameters and a final `tool_context: ToolContext = None`. Example
(`agents/em/tools/analytics/query_interaction_health.py`):

```python
async def query_interaction_health(
    top_n: int = 10,
    interaction_names: list[str] = None,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    filters: str = None,
    tool_context: ToolContext = None,
) -> dict:
    ...
```

ADK introspects the signature and wraps the callable as a `FunctionTool` when
it is passed to an `Agent(... tools=[fn, ...])` constructor. Tools always
return a dict that downstream callers parse as
`{"status": "success" | "error", "data": ..., "message": ...}` (the exact
shape is documented per tool in [`../tools/`](../tools/)).

### Agent instances

- `em_agent = Agent(model=AGENT_MODEL, name=EM_AGENT_NAME,
  instruction=build_system_prompt, output_key='engineering_manager_result',
  tools=[query_interactions, query_alerts, query_interaction_health,
  query_interaction_metrics, query_interaction_sessions,
  breakdown_interaction, calculate])` — see `agents/em/agent.py`.
- `rca_agent = LlmAgent(model=AGENT_MODEL, name=RCA_AGENT_NAME,
  instruction=build_rca_prompt, tools=[],
  output_schema=RcaStructuredReportV1,
  output_key="rca_structured_report",
  include_contents="default")` — see `agents/rca/agent.py`.
- `report_agent = create_report_agent()` factory in `agents/report/agent.py`
  returns a fresh `LlmAgent` per call. The factory exists because ADK forbids
  an agent from having multiple parents — both the EM pipeline and the RCA
  pipeline need their own `report_agent` instance.
- `screen_rca_narrative_agent` — `LlmAgent` with structured output schema
  `ScreenRcaNarrativeV1`.

### Model

`AGENT_MODEL = os.getenv("AGENT_MODEL", "gemini-2.5-flash")` (`constants.py`).
Every agent reuses the same model id so a single env flip swaps the entire
pipeline.

### Output handoff

`em_agent.output_key='engineering_manager_result'` writes the EM agent's final
text into ADK session state under that key. `report_agent`'s prompt
(`build_report_prompt`) reads `ctx.state.get("engineering_manager_result")` and
splices it into its system prompt. This is the only handoff between the two
agents in the sequential pipeline.

## Dependencies

- `google.adk` — agents, runners, sessions, tools.
- `pulse_ai.schemas.*` — Pydantic structured-output schemas for RCA agents.
- `dotenv` — `.env` loading in agent modules.

## Data contracts

- Tool input: derived from function signatures; ADK enforces types.
- Tool output: `dict` with `status` + payload keys.
- Agent output: free-form text (EM, Report) or schema-validated JSON
  (RCA → `RcaStructuredReportV1`, Screen RCA → `ScreenRcaNarrativeV1`).

## Tests

- `tests/test_agent.py` — root agent wiring.
- `tests/test_rca_agent.py` — RCA agent structured-output contract.
- `tests/test_report_agent.py` — report agent prompt + tools.
- `tests/test_analytics_tools.py`, `test_config_tools.py`,
  `test_calculate_tool.py`, `test_interaction_templates.py` — per-tool tests.

## History / decisions

- `Agent` (alias for `LlmAgent`) used for the EM persona — same class, picked
  for clarity that the EM agent uses tool-calling.
- RCA agent has `tools=[]` and relies on `output_schema` for schema enforcement.
- Report agent factory pattern is enforced by ADK's "one parent per agent" rule.

## Rebuild recipe

```python
from google.adk.agents.llm_agent import Agent
from google.adk.agents.sequential_agent import SequentialAgent

em_agent = Agent(model=AGENT_MODEL, name="EMAgent",
                 instruction=build_system_prompt,
                 output_key='engineering_manager_result',
                 tools=[...])

report_agent = create_report_agent()  # factory!

root_agent = SequentialAgent(name='root_agent',
                             sub_agents=[em_agent, report_agent])
```
