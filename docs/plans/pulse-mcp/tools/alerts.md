# pulse-mcp / alerts tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-mcp.md](../../../components/pulse-mcp.md).

## Purpose

Read access to the Pulse alerting domain: alert list, evaluation history, and the
metadata an AI client needs to describe alerts to a user (scopes, metrics,
severities, filter options, notification channels).

## Source location

`pulse-mcp/src/tools/alerts.ts` — `registerAlertTools(server)`.

## Public surface

Seven read-only tools. All require `projectId: string` and forward it as
`X-Project-ID`.

| Tool | Endpoint | Extra params |
|---|---|---|
| `list_alerts` | `GET /v1/alert` | `search?`, `severity?`, `scope?` |
| `get_alert_evaluation_history` | `GET /v1/alert/{alertId}/evaluationHistory` | `alertId` |
| `get_alert_filters` | `GET /v1/alert/filters` | — |
| `get_alert_scopes` | `GET /v1/alert/scopes` | — |
| `get_alert_metrics` | `GET /v1/alert/metrics` | — |
| `get_alert_severities` | `GET /v1/alert/severity` | — |
| `list_alert_notification_channels` | `GET /v1/alert/notificationChannels` | — |

Return shape: `{ content: [{ type: "text", text: <pretty JSON> }] }` for all.

## Internal design

Each handler is a one-line `getClient().get(path, projectId, params?)`. There is
no mutation surface here — alert creation/update lives in the dashboard.

## Dependencies

- `PulseClient` (`src/client.ts`) — JWT injection, `X-Project-ID` header,
  `{data}` unwrap.
- `zod` schemas for inputs.

## Data contracts

- `severity`, `scope`, and `search` mirror the pulse-server query parameters
  consumed by `/v1/alert`.
- `evaluationHistory` returns the same payload `pulse-alerts-cron` produces
  (see `backend/pulse-alerts-cron/`).

## Tests

E2E only.

## History / decisions

- The seven endpoints map 1:1 to pulse-server's alert read surface. Filter
  metadata endpoints (`/filters`, `/scopes`, `/metrics`, `/severity`,
  `/notificationChannels`) are exposed individually so the agent can populate
  pick-lists without inferring values from a sample alert.
- No write tools (create/update/delete) — `pulse-mcp` is intentionally read-only.

## Rebuild recipe

For each read endpoint, register a tool that:
1. Takes `projectId` and any pulse-server-defined filters via `zod`.
2. Calls `getClient().get(path, projectId, params)`.
3. Wraps the JSON-stringified result in MCP text content.

Wire from `src/index.ts` via `registerAlertTools(server)`.
