# pulse-mcp

## What

A read-only [Model Context Protocol](https://modelcontextprotocol.io/) server that
exposes the Pulse HTTP API as MCP tools. AI clients (Claude, Cursor, custom hosts)
can list projects, fetch metrics, sessions, alerts, heatmaps, funnels, journeys,
events, interactions, SDK config, anomalies, and App Vitals (crashes, ANRs, non-fatal
issues) — all through the standard MCP `tools/list` + `tools/call` surface.

The server is a thin shim. It does no aggregation: every tool call maps 1:1 to one
or two Pulse REST endpoints, JSON-serialises the response, and returns it as MCP
text content.

## Path and stack

- Source root: `pulse-mcp/`
- Language: TypeScript (ESM, `"type": "module"`)
- Runtime: Node.js 18+ (`engines.node >=18.0.0`)
- Key dependencies:
  - `@modelcontextprotocol/sdk ^1.12.0` — MCP server + stdio transport
  - `axios ^1.7.0` — HTTP client with request/response interceptors
  - `zod ^3.23.0` — tool input schema validation
- Transport: **stdio only** (one MCP host process spawns one server process)
- Package name: `@dreamhorizonorg/pulse-mcp` (version `0.1.0`)

## Build and run

```bash
cd pulse-mcp
npm install            # or yarn install (yarn 4 packageManager pinned)
npm run build          # tsc → dist/
npm start              # node dist/index.js
npm run dev            # tsc --watch
npm run typecheck      # tsc --noEmit
```

`prepare` runs `npm run build`, so a fresh `npm install` produces a runnable `dist/`.

## Inputs and outputs

### Inputs (environment)

| Variable | Required | Purpose |
|---|---|---|
| `PULSE_BASE_URL` | yes | Pulse API origin, no trailing slash, e.g. `http://localhost:8080` |
| `PULSE_API_KEY` | yes | Personal MCP key (`pulse_mcp_…`). Exchanged on startup and on every 401. |

Personal keys are minted in the Pulse UI under **Personal Access Tokens** (route
`/account/tokens`). Project API keys do **not** work here.

### Inputs (per tool call)

Tools are invoked by an MCP host. Every tool schema is declared with `zod`. Most
tools require `projectId: string`; the client forwards it as the `X-Project-ID`
header (see `pulse-mcp/src/client.ts`).

### Outputs

Each tool returns:

```json
{ "content": [ { "type": "text", "text": "<pretty-printed JSON from Pulse>" } ] }
```

Errors bubble as MCP tool errors (axios rejection on 4xx/5xx after the 401 retry
path is exhausted).

### Auth lifecycle

1. On boot, `src/index.ts` calls `exchangeApiKeyForTokens(baseUrl, apiKey)` against
   `POST /v1/auth/api-key/exchange`, persists `{accessToken, refreshToken}` to
   `~/.pulse-mcp/credentials.json` (mode `0600`).
2. The axios request interceptor injects `Authorization: Bearer <accessToken>`.
3. On any `401`, the response interceptor re-exchanges the original API key
   (not the refresh token), saves new credentials, and replays the request once.
4. `loadCredentials` reads `~/.pulse-mcp/credentials.json` and throws if missing.

## Key files

| File | Role |
|---|---|
| `src/index.ts` | Entrypoint. Validates env, exchanges API key, instantiates `McpServer`, registers every tool group, connects `StdioServerTransport`. |
| `src/auth.ts` | `exchangeApiKeyForTokens`, `loadCredentials`, `saveCredentials`. `Credentials` interface. |
| `src/client.ts` | `PulseClient` axios wrapper: bearer header, 401 re-exchange-and-retry, `X-Project-ID` injection, optional `raw` mode for endpoints that don't wrap responses in `{data:…}`. |
| `src/tools/projects.ts` | `list_projects`, `get_project`, `list_project_members`. |
| `src/tools/metrics.ts` | `get_apdex_score`, `get_error_rate`, `get_interaction_time`, `get_interaction_categorization`. |
| `src/tools/sessions.ts` | `list_session_replays`. |
| `src/tools/alerts.ts` | `list_alerts`, `get_alert_evaluation_history`, `get_alert_filters`, `get_alert_scopes`, `get_alert_metrics`, `get_alert_severities`, `list_alert_notification_channels`. |
| `src/tools/appVitals.ts` | Crash/ANR/non-fatal issue lists, issue summary, trend, stack traces, screen breakdown, first/last seen. |
| `src/tools/appVitalsHelpers.ts` | Distribution query builder + filter helpers used by App Vitals tools. |
| `src/tools/appVitalsConstants.ts` | Column-name constants (`PulseType`, `UserId`, …) and `PULSE_TYPE_SESSION_START`. |
| `src/tools/events.ts` | Event-catalog tools: `list_event_definitions`, `get_event_definition`, `list_event_categories`, `search_events`. |
| `src/tools/funnels.ts`, `journeys.ts`, `heatmap.ts`, `interactions.ts`, `query.ts`, `sdkConfig.ts`, `anomaly.ts` | Additional read-only tool groups. |

## Plan

Detailed per-tool breakdown lives under
[`/docs/plans/pulse-mcp/`](../plans/pulse-mcp/index.md). Each tool family has its
own page listing parameters, return shape, the underlying Pulse endpoint, and the
exact source path.
