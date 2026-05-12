# pulse-ai · Orchestration

How the `root_agent` composes sub-agents (Sequential / Parallel / Loop) to answer multi-step observability questions.

Brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md) · Peers: [../core/agent-server](../core/agent-server.md), [../core/prompt-personas](../core/prompt-personas.md), [clickhouse-tools](./clickhouse-tools.md), [mysql-tools](./mysql-tools.md).

## Source location

- `pulse_ai/agent.py` — exports `root_agent`.
- `pulse_ai/agents/` — personas/specialists:
  - `agents/rca/` — root-cause analysis agent.
  - `agents/screen_rca/` — screen-level RCA variant.
  - `agents/report/` — report-writing agent.
  - `agents/em/` — engineering-manager persona.
- `pulse_ai/server/rca_runner.py`, `server/screen_rca_runner.py` — SSE runners that stream the agent's output.

## Public surface

- `root_agent` object (Google ADK).
- REST endpoints exposed by `server/app.py`:
  - `POST /run/rca` (streams SSE via `rca_runner`).
  - `POST /run/screen-rca`.
  - Plus a chat endpoint used by the UI `AiChat` screen.

## Internal design

1. ADK orchestration primitives:
   - `SequentialAgent` — chains planner → executor → summarizer.
   - `ParallelAgent` — fan-out read-only lookups (ClickHouse + MySQL in parallel).
   - `LoopAgent` — iterative refinement until `done_condition` holds.
2. Tools (see sibling files) are registered as `FunctionTool` instances returning `{"status": ..., "data": ...}`.
3. Session scope: `server/session_scope_store.py` persists per-conversation state (selected project, time range, chosen persona).
4. Auth: `tool_session_auth.py` — tools accept a session token that carries the user's project scope to prevent cross-tenant reads.

## Dependencies

- Google ADK (Agent Development Kit).
- Gemini via `GOOGLE_API_KEY`.
- Internal HTTP calls to `backend-server` for policy/auth.

## Data contracts

- Tool return shape: `{"status": "ok" | "error", "data": <json>, "error"?: str}`.
- SSE chunks: `{"type": "thought" | "tool_call" | "tool_result" | "final", "content": ...}`.

## Tests

- `pulse_ai/tests/` — pytest suite; mocks ADK runtime for deterministic orchestration tests.
- Integration: a staging project with seed data validates end-to-end RCA.

## History / decisions

Separate `rca` and `screen_rca` agents so prompt surface can diverge — screen-level questions benefit from tighter tool access and a different summarizer tone.

## Rebuild recipe

1. Define your tools (see [clickhouse-tools.md](./clickhouse-tools.md), [mysql-tools.md](./mysql-tools.md)).
2. Build a persona prompt in `agents/<name>/`.
3. Compose with `SequentialAgent([plan, execute, summarize])`.
4. Expose via an SSE runner in `server/`.
5. Gate with `tool_session_auth` so tools can enforce the caller's project.
