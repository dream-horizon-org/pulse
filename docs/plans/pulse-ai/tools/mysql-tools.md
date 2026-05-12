# pulse-ai / tools / mysql-tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-ai.md](../../../components/pulse-ai.md).

## Purpose

Configuration-domain tools used by the EM persona. These hit pulse-server REST
endpoints that read from the MySQL operational store (project/interaction
configurations, alert definitions) — **not** ClickHouse. They give the agent
the catalog it needs before issuing analytics queries.

## Source location

`pulse_ai/agents/em/tools/config/`:

- `query_interactions.py` — `query_interactions(scope, ...)`.
- `query_alerts.py` — `query_alerts(...)`.

Shared:

- `pulse_ai/client/pulse_client.py` — async HTTP client.
- `pulse_ai/agents/em/transformers/response_transformer.py` —
  `parse_error_response`.
- `pulse_ai/tool_session_auth.py` — auth header extraction.

## Public surface

### `query_interactions`

- Signature: `query_interactions(scope, interaction_name=None, page=0,
  size=10, name=None, status="RUNNING", tool_context=None)`.
- `scope` is one of `VALID_SCOPES = ("list", "detail", "filters", "telemetry_filters")`.
  - `list` → `GET /v1/interactions?page&size&name&status` (paginated list of
    configured interactions, filterable by `status` `RUNNING`/`STOPPED`).
  - `detail` → `GET /v1/interactions/{interaction_name}` (single interaction
    definition).
  - `filters` → `GET /v1/interactions/filters` (available filter
    metadata used by the dashboard).
  - `telemetry_filters` → `GET /v1/interactions/telemetry-filters` (telemetry
    dimensions usable in distribution queries).
- Return: `{"status": "success", "data": <pulse-server payload>}` or
  `{"status": "error", "message": "Invalid scope ..."}` / mapped error.

### `query_alerts`

- Reads alert configurations and (optionally) evaluation history from
  pulse-server's alert REST surface (`/v1/alert`, `/v1/alert/scopes`,
  `/v1/alert/severity`, `/v1/alert/metrics`, `/v1/alert/notificationChannels`,
  `/v1/alert/{alertId}/evaluationHistory`).
- Same scope-style switch as `query_interactions`; lets the LLM enumerate
  alerts before drilling into evaluation history.

Both tools follow the standard envelope and report auth errors via
`pulse_tool_session_auth_error(tool_context)` if `Authorization` or
`X-Project-ID` is missing.

## Internal design

1. Validate `scope` (whitelist).
2. Pull `Authorization` and `X-Project-ID` from `tool_context` state via
   `pulse_tool_session_auth_error`.
3. Build the path/query params for the chosen scope.
4. `async with PulseClient(...) as client: await client.get(path, params=...)`.
5. Map non-2xx via `parse_error_response`; otherwise return the JSON body.

`query_interactions` and `query_alerts` are GET-only — these endpoints don't
take a `DistributionRequestBody` because the underlying data is small
MySQL-backed config, not a time-series query.

## Dependencies

- `httpx` via `PulseClient`.
- `google.adk.tools.ToolContext`.

## Data contracts

- `status` enum values for `query_interactions` list scope: `RUNNING`,
  `STOPPED` (default `RUNNING`).
- Pagination is zero-based (`page=0`, `size=10`).
- The MySQL→REST schema is owned by pulse-server (`backend/server/`); these
  tools are pure read-throughs.

## Tests

- `tests/test_config_tools.py` — scope validation, header propagation, error
  mapping for `query_interactions` and `query_alerts`.
- `tests/test_pulse_client.py` — header behavior shared with analytics tools.

## History / decisions

- A single `scope` enum keeps the tool count small — the LLM picks the right
  read pattern without juggling four near-identical tool names.
- Validation happens client-side because an invalid `scope` should not even
  attempt an HTTP request (saves tokens on a guaranteed-bad call).
- Mutation surface (create/update/delete interactions and alerts) is not
  exposed — Pulse AI is intentionally read-only against MySQL state.

## Rebuild recipe

```python
VALID_SCOPES = ("list", "detail", "filters", "telemetry_filters")

async def query_interactions(scope, interaction_name=None, page=0, size=10,
                             name=None, status="RUNNING", tool_context=None):
    if scope not in VALID_SCOPES:
        return {"status": "error",
                "message": f"Invalid scope '{scope}'. Valid: {VALID_SCOPES}"}
    headers = pulse_tool_session_auth_error(tool_context)
    if headers.get("status") == "error":
        return headers
    path, params = _path_for_scope(scope, interaction_name, page, size, name, status)
    async with PulseClient(**headers["data"]) as client:
        resp = await client.get(path, params=params)
    if resp.status_code >= 400:
        return parse_error_response(resp)
    return {"status": "success", "data": resp.json()}
```
