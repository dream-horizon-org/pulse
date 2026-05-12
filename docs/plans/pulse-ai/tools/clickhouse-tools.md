# pulse-ai / tools / clickhouse-tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md).

## Purpose

Analytics tools used by the EM persona. Each one composes a Pulse "distribution"
query template and posts to a pulse-server REST endpoint; pulse-server in turn
translates the request into a ClickHouse query against `otel_traces` and
`otel_logs` (with the materialised `ProjectId`, `PulseType`, `Platform`,
`AppVersion`, `SessionId` columns). The agent never speaks ClickHouse directly —
it composes the same `DistributionRequestBody` shape used by `pulse-ui`.

## Source location

`pulse_ai/agents/em/tools/analytics/`:

- `query_interaction_health.py` — `query_interaction_health(...)`
- `query_interaction_metrics.py` — `query_interaction_metrics(...)`
- `query_interaction_sessions.py` — `query_interaction_sessions(...)`
- `breakdown_interaction.py` — `breakdown_interaction(...)`

Shared:

- `pulse_ai/agents/em/templates/` — query templates (e.g. `build_health_query`,
  `TIME_RANGE_DOC`).
- `pulse_ai/agents/em/transformers/response_transformer.py` —
  `transform_columnar`, `parse_error_response`.
- `pulse_ai/client/pulse_client.py` — `PulseClient` async HTTP wrapper.
- `pulse_ai/tool_session_auth.py` — `pulse_tool_session_auth_error`.

## Public surface

### `query_interaction_health`

- Returns Apdex, error rate, P50 latency, and user categorization for the top
  N interactions over a time range.
- Endpoint: `POST /v1/interactions/performance-metric/distribution` (constant
  `DATA_QUERY_PATH` in the tool).
- Key params: `top_n` (default 10), `interaction_names?`,
  `time_range` (default `"last_24h"`, see `TIME_RANGE_DOC`),
  `start_time?` / `end_time?` (for `time_range="custom"`),
  `filters?` (JSON string with keys like `platform`, `appVersion`, …).

### `query_interaction_metrics`

- Returns the composite metric bundle for a **single** interaction (Apdex,
  latency, crash, ANR, error counts, user categories).
- Endpoint: `POST /v1/interactions/{interactionName}/metrics/distribution`
  (composite metric type).
- Params: `interaction_name`, `time_range`, `metric_type`
  (`"composite"` for full stats, or narrower types), telemetry filters.

### `query_interaction_sessions`

- Returns session-level rows for an interaction (timestamps, session ids,
  outcome) so the agent can cite sessions in its answer.
- Endpoint: `POST /v1/interactions/{interactionName}/sessions/distribution`.
- Params: time range, filters, pagination/limit.

### `breakdown_interaction`

- Dimensional breakdown: slice an interaction's metrics by `platform`,
  `appVersion`, `osVersion`, `deviceModel`, `networkProvider`, or `state`.
- Endpoint: same distribution surface, with the breakdown dimension in the
  request body.

All four return the standard ADK tool envelope:

```python
{"status": "success", "data": <transformed rows>}
# or
{"status": "error", "message": "<reason>"}
```

`transform_columnar(response)` reshapes the pulse-server columnar payload into
a list of row dicts before returning.

## Internal design

```text
Agent tool fn
   │ build_*_query()  (templates/interaction_templates.py)
   ▼
DistributionRequestBody  (dict matching pulse-ui's DistributionRequestBody)
   │ PulseClient.post(path, body)  (httpx.AsyncClient, auth headers)
   ▼
pulse-server  /v1/interactions/.../distribution
   │ translates → ClickHouse SQL
   ▼
otel_traces / otel_logs   (project-scoped via row policies)
```

Auth: every tool fetches the `Authorization` and `X-Project-ID` headers from
`tool_context` via `pulse_tool_session_auth_error`; on missing headers it
returns the canonical error dict immediately.

Time ranges accept named windows (`last_24h`, `last_7d`, …) plus `"custom"`
with explicit ISO `start_time`/`end_time`. `TIME_RANGE_DOC` is appended to
every tool's docstring so the LLM sees the same enum everywhere.

## Dependencies

- `httpx` (via `PulseClient`).
- `google.adk.tools.ToolContext`.
- Templates in `agents/em/templates/` and transformers in
  `agents/em/transformers/`.

## Data contracts

Always include (per `CLAUDE.md` ClickHouse rules, enforced by pulse-server):

- Time-range filter on `Timestamp`.
- `ProjectId` filter (set automatically by `X-Project-ID` + row policies).
- `LIMIT`.

Materialised columns the templates rely on: `ProjectId`, `PulseType`,
`Platform`, `AppVersion`, `SessionId`, `UserId`, `OsVersion`, `DeviceModel`.

## Tests

- `tests/test_analytics_tools.py` — per-tool happy path + transformer wiring.
- `tests/test_interaction_templates.py` — template builders.
- `tests/test_transformers.py` — columnar → row-dict reshape.
- `tests/test_pulse_client.py` — `PulseClient` headers + error mapping.

## History / decisions

- Tools call pulse-server (not ClickHouse) so multi-tenant row policies stay
  in one place.
- `metric_type="composite"` exists because the LLM kept calling three tools in
  series for the same data; composite collapses it to one round trip.
- `filters` is a JSON string, not a typed dict, because the LLM produces it
  more reliably as a string than as a structured arg (avoids partial dicts).

## Rebuild recipe

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
    headers = pulse_tool_session_auth_error(tool_context)
    if headers.get("status") == "error":
        return headers
    body = build_health_query(top_n, interaction_names, time_range,
                              start_time, end_time, filters)
    async with PulseClient(**headers["data"]) as client:
        resp = await client.post(DATA_QUERY_PATH, body)
    if resp.status_code >= 400:
        return parse_error_response(resp)
    return {"status": "success", "data": transform_columnar(resp.json())}
```
