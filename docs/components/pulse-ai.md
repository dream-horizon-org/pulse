# pulse-ai

## What

The Pulse AI agent server. A FastAPI service wrapping a [Google ADK](https://google.github.io/adk-docs/)
agent pipeline that answers natural-language questions about Pulse observability
data and emits structured root-cause analyses. The HTTP surface streams ADK runs
as Server-Sent Events; the AI brain is a `SequentialAgent` pipeline driven by
Gemini (`gemini-2.5-flash` by default).

Pulse AI is not a static pipeline: agents reason, call tools, delegate, and emit
both prose responses and structured JSON reports (`RcaStructuredReportV1`,
`ScreenRcaNarrativeV1`, etc.).

## Path and stack

- Source root: `pulse_ai/`
- Language: Python 3.11+
- Framework: Google ADK (`google.adk.agents`, `google.adk.runners`, `google.adk.tools`)
- LLM: Gemini, model id from env `AGENT_MODEL` (default `gemini-2.5-flash` — see
  `pulse_ai/constants.py`)
- HTTP server: FastAPI + SSE streaming, runs on **port 8000**
- Sessions: ADK `InMemorySessionService` by default, `DatabaseSessionService`
  when `SESSION_DB_URL` is set (async SQLAlchemy URL via `to_async_sqlalchemy_url`)
- Dockerised via `pulse_ai/Dockerfile` + `docker-compose.yml`

## Build and run

```bash
cd pulse_ai
./setup.sh                       # starts the Docker stack on :8000
# or, for local dev:
pip install -r requirements.txt
uvicorn pulse_ai.server.app:app --port 8000
pytest                           # full test suite (see tests/)
```

Environment:

| Variable | Required | Purpose |
|---|---|---|
| `GOOGLE_API_KEY` | yes | Gemini API key used by ADK. |
| `AGENT_MODEL` | no | Override default Gemini model. |
| `PULSE_BASE_URL` | no | Pulse-server origin (default `http://localhost:8080`). |
| `SESSION_DB_URL` | no | When set, ADK sessions persist to a DB; otherwise in-memory. |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated list; defaults include `localhost:3000/3001`. |
| `LOG_LEVEL` | no | Defaults to `INFO`. |

## Inputs and outputs

### Inputs

- HTTP requests to FastAPI routes in `pulse_ai/server/routes.py`. The main entry
  is the SSE `run` endpoint (request shape `RunSSERequest` defined in
  `pulse_ai/server/app.py`).
- Per-request headers: `Authorization: Bearer <jwt>` and `X-Project-ID` (validated
  by `pulse_ai/server/project_headers.py` and forwarded to the Pulse backend via
  `PulseClient`).
- For each session, the user's prompt; ADK fills `tool_context.state` with the
  prior `engineering_manager_result`, etc.

### Outputs

- Streaming SSE chat responses from the EM → Report pipeline.
- Structured JSON reports for the dedicated runners:
  - `rca_runner` → `RcaStructuredReportV1` (see `schemas/rca_structured_v1.py`)
  - `screen_rca_runner` → `ScreenRcaNarrativeV1` (`schemas/screen_rca_narrative_v1.py`)
  - `root_cause_fetch` → `RootCausePayloadSchema` from the Pulse backend.
- Each ADK tool returns the contract `{"status": "...", "data": ...}` (or
  `{"status": "error", "message": "..."}`).

## Key files

| File | Role |
|---|---|
| `agent.py` | Defines `root_agent` as a `SequentialAgent(sub_agents=[em_agent, report_agent])`. |
| `constants.py` | `APP_NAME`, `AGENT_MODEL`, agent name constants, timeouts, Pulse base URL helper. |
| `agents/__init__.py` | Re-exports `em_agent`, `report_agent`, `rca_agent`. |
| `agents/em/agent.py` | EM persona — `Agent` instance with 7 analytics tools and callable instruction `build_system_prompt`. |
| `agents/em/prompts.py` | `build_system_prompt(ctx)` — injects current UTC time, behaviour rules. |
| `agents/em/tools/` | Config (`query_interactions`, `query_alerts`), analytics (`query_interaction_health`, `query_interaction_metrics`, `query_interaction_sessions`, `breakdown_interaction`), utils (`calculate`). |
| `agents/rca/agent.py` | Reasoning-only `LlmAgent` with `output_schema=RcaStructuredReportV1`. |
| `agents/report/agent.py` | `LlmAgent` with `create_chart`, `create_table` tools; factory `create_report_agent()` because ADK forbids re-parenting an agent. |
| `agents/screen_rca/` | Screen-level RCA narrative agent. |
| `server/app.py` | FastAPI app factory, `Runner` instances (`runner`, `rca_runner`, `screen_rca_runner`), session service, CORS, auth middleware. |
| `server/routes.py` | HTTP route handlers (`run_sse`, RCA endpoints). |
| `server/run_sse_utils.py` | SSE stream helpers (`stream_adk_run_as_sse`, `ensure_session_for_run`). |
| `server/rca_runner.py`, `screen_rca_runner.py` | Wrap the RCA / screen-RCA `LlmAgent` in a `Runner` and produce typed reports. |
| `server/root_cause_fetch.py` | Calls pulse-server `/v1/interactions/{interaction}/root-cause` for tabular RCA inputs. |
| `client/pulse_client.py` | Async `httpx` client with `Authorization` + `X-Project-ID` headers. |
| `schemas/` | Pydantic schemas for structured outputs (`rca_structured_v1`, `screen_rca_narrative_v1`, `error_attribution_rca`, `root_cause`). |
| `tool_session_auth.py` | `pulse_tool_session_auth_error` — uniform error when tool context lacks auth headers. |
| `tests/` | pytest suite covering agents, tools, runners, serializers, transformers. |

### Personas

The shipped agent set is **EM (Engineering Manager)**, **RCA**, **Report**, and
**Screen RCA**. The `README.md` documents a broader persona model (Product
Analytics, Engineering Manager, Designer, Customer Success, Business Leaders)
that the codebase is converging on; the EM agent is the first concrete persona.

## Plan

Per-component design pages live under
[`/docs/plans/pulse-ai/`](../plans/pulse-ai/index.md):

- `core/agent-server.md` — FastAPI boot, SSE runner, session service.
- `core/adk-setup.md` — ADK agent types, runners, tool contract.
- `core/prompt-personas.md` — EM / RCA / Report / Screen-RCA prompt structure.
- `tools/clickhouse-tools.md` — analytics tools that hit OTLP-backed endpoints
  (ultimately querying `otel_traces` / `otel_logs`).
- `tools/mysql-tools.md` — config tools (`query_interactions`, `query_alerts`)
  that hit MySQL-backed Pulse REST endpoints.
- `tools/orchestration.md` — `SequentialAgent` / `ParallelAgent` / `LoopAgent`
  composition and the EM → Report sequencing contract.
