# pulse-ai / core / agent-server

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md).

## Purpose

Boot the FastAPI process that fronts every ADK agent. Hosts the EM → Report
pipeline `Runner`, the RCA `Runner`, and the screen-RCA `Runner` against a
shared session service, and exposes them as SSE streaming HTTP endpoints.

## Source location

`pulse_ai/server/`:

- `app.py` — FastAPI factory + shared services.
- `routes.py` — HTTP handlers (`run_sse`, RCA endpoints).
- `middleware.py` — `AuthMiddleware`.
- `project_headers.py` — `require_x_project_id`.
- `run_sse_utils.py` — `ensure_session_for_run`, `stream_adk_run_as_sse`,
  `request_headers_to_state_delta`, `user_content_from_parts`.
- `rca_runner.py` — `generate_rca_report` (+ `RcaRunnerError`).
- `screen_rca_runner.py` — `generate_screen_rca_report`.
- `root_cause_fetch.py` — fetches tabular RCA input from pulse-server.
- `serializers.py` — `DeltaTracker`, `events_to_messages`,
  `extract_content_blocks`, `extract_title`.
- `session_scope_store.py` — `create_session_scope_store`,
  `to_async_sqlalchemy_url`.

## Public surface

The FastAPI `app` (in `pulse_ai/server/app.py`) is the only export consumed by
the runtime. Routes (declared in `routes.py`) cover:

- SSE `run` endpoint (`RunSSERequest` → streaming response from `root_agent`).
- RCA endpoints that call `generate_rca_report` / `generate_screen_rca_report`.
- Root-cause tabular fetch helper `fetch_root_cause_payload(...)`.

## Internal design

1. **Module load:** `load_dotenv()` and `logging.basicConfig(...)`.
2. **CORS origins:** `_get_cors_origins()` reads `CORS_ALLOWED_ORIGINS` or falls
   back to `DEFAULT_CORS_ORIGINS` (`localhost:3000/3001`).
3. **Session service:** `_create_session_service()` returns
   `DatabaseSessionService(db_url=to_async_sqlalchemy_url(...))` when
   `SESSION_DB_URL` is set, else `InMemorySessionService()`.
4. **FastAPI app:** `FastAPI(title="Pulse AI Agent Server")`,
   `AuthMiddleware` + `CORSMiddleware` registered.
5. **Runners:** three `Runner` instances, all on the shared `session_service`
   and `app_name=APP_NAME`:
   - `runner` — `agent=root_agent` (EM → Report sequential pipeline).
   - `rca_runner` — `agent=rca_agent`, `auto_create_session=True`.
   - `screen_rca_runner` — `agent=screen_rca_narrative_agent`.
6. **Session scope store:** `create_session_scope_store(SESSION_DB_URL)` for
   per-session project scope persistence.

## Dependencies

- `fastapi`, `starlette` (CORS), `pydantic`
- `google.adk.runners.Runner`
- `google.adk.sessions.InMemorySessionService` / `DatabaseSessionService`
- `pulse_ai.agent.root_agent`, `pulse_ai.agents.rca.rca_agent`,
  `pulse_ai.agents.screen_rca.screen_rca_narrative_agent`
- `pulse_ai.constants` (timeouts, app name, CORS defaults)

## Data contracts

- Request: `RunSSERequest` (Pydantic) — message text, session ID, etc.
- Response: `text/event-stream` of ADK events serialised by
  `stream_adk_run_as_sse(...)`.
- Auth: `Authorization` + `X-Project-ID` headers required; `AuthMiddleware`
  rejects requests without them and `require_x_project_id` enforces the project
  header per route.
- ADK tools read the JWT via `tool_context` (see `tool_session_auth.py`).

## Tests

- `tests/test_session_routes_scope.py`, `tests/test_session_scope_store.py` —
  scope store behavior.
- `tests/test_serializers.py` — `events_to_messages` and friends.
- `tests/test_tool_session_auth.py` — auth error contract.
- `tests/test_rca_runner_prompt_order.py` — RCA runner prompt assembly.

## History / decisions

- `DatabaseSessionService` requires an async SQLAlchemy URL — hence the
  `to_async_sqlalchemy_url(...)` mapper for plain `sqlite://` inputs.
- `rca_runner` uses `auto_create_session=True` because the RCA HTTP endpoint
  is stateless: each request is its own short-lived ADK session.
- Timeouts (`BACKEND_REQUEST_TIMEOUT_SECONDS=75`,
  `RCA_PIPELINE_TIMEOUT_SECONDS=300`) are centralised in `constants.py` so
  upstream / downstream limits are visible in one place.

## Rebuild recipe

```python
from fastapi import FastAPI
from google.adk.runners import Runner
from pulse_ai.agent import root_agent

app = FastAPI(title="Pulse AI Agent Server")
session_service = InMemorySessionService()
runner = Runner(agent=root_agent, app_name="pulse_ai",
                session_service=session_service)
```

Then wire `routes.py` handlers to `stream_adk_run_as_sse(runner, ...)`.
