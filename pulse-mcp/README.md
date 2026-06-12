# pulse-mcp

A read-only [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server for Pulse: it exposes Pulse HTTP APIs as MCP tools so assistants can list projects, inspect metrics, sessions, alerts, heatmaps, funnels, journeys, events, interactions, SDK config, and **App Vitals** (crashes, ANRs, non-fatal issues, issue detail).

Transport: **stdio** (local process). Node **18+** required.

## Prerequisites

- A running Pulse API (`pulse-server` or your deployed base URL).
- A **personal MCP API key** from the Pulse UI: open **Personal Access Tokens** from the user menu (route **`/account/tokens`**). Keys are prefixed with `pulse_mcp_`. (Project **Settings → API Keys** are project keys, not personal MCP keys.)

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `PULSE_BASE_URL` | Yes | API origin without a trailing slash, e.g. `http://localhost:8080` |
| `PULSE_API_KEY` | Yes | Personal API key; exchanged for JWTs on startup and on 401 |

On startup, the server calls `POST /v1/auth/api-key/exchange` with body `{ "apiKey": "<PULSE_API_KEY>" }` and writes tokens to **`~/.pulse-mcp/credentials.json`** (mode `0600`). The Axios client sends the access token as `Authorization: Bearer …`. Tools that send **`user-email`** (e.g. session listing when the JWT includes an **`email`** claim) derive it from that token. If a request returns **401**, the client **re-exchanges** the same `PULSE_API_KEY` (not the refresh token), overwrites the credentials file, and retries the request once.

`PULSE_API_KEY` must remain set in the MCP config: the 401 recovery path needs it.

## Install and build

```bash
cd pulse-mcp
npm install   # or: yarn install (see package.json "packageManager")
npm run build
```

- `npm run dev` — watch mode (`tsc --watch`)
- `npm start` — run compiled server (`node dist/index.js`)
- `npm run typecheck` — typecheck only (`tsc --noEmit`)

## Quick smoke test (stdio)

After build:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | PULSE_BASE_URL=http://localhost:8080 \
    PULSE_API_KEY="pulse_mcp_YOUR_KEY" \
    node dist/index.js
```

You should see stderr lines such as `Exchanging API key for tokens...`, `Authentication successful.`, `Pulse MCP server running on stdio`, then a JSON `result.tools` array on stdout.

## Cursor / Claude Desktop

Point `command`/`args` at `dist/index.js` and pass env (example for Cursor: `~/.cursor/mcp.json` or project `.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "pulse": {
      "command": "node",
      "args": ["/absolute/path/to/pulse/pulse-mcp/dist/index.js"],
      "env": {
        "PULSE_BASE_URL": "http://localhost:8080",
        "PULSE_API_KEY": "pulse_mcp_YOUR_KEY_HERE"
      }
    }
  }
}
```

For a manual end-to-end check: create a key at **`/account/tokens`**, point this MCP at your `PULSE_BASE_URL`, set `PULSE_API_KEY`, run the smoke command above, then revoke the key in the UI and confirm a new exchange fails until you create a new key.

## App Vitals tools

These tools call the same endpoint as the Pulse UI: **`POST /v1/interactions/performance-metric/distribution`**, with request bodies aligned to `pulse-ui` hooks (`useExceptionListData`, `useIssueDetailData`, etc.). Column names are duplicated in [`src/tools/appVitalsConstants.ts`](src/tools/appVitalsConstants.ts); **keep them in sync** with [`pulse-ui/src/constants/PulseOtelSemcov.ts`](../pulse-ui/src/constants/PulseOtelSemcov.ts) when semconv fields change.

### Authorization

The backend enforces **`@RequiresPermission("can_view")`** on distribution. The JWT from your personal API key must have the same effective project access as an interactive user for this route. **HTTP 403** usually means missing role/OpenFGA permission, not an MCP bug—tool responses include status and a short server message when available.

### Pagination

The server `QueryRequest` model supports **`limit`** but **not `offset`** for distribution queries. List tools expose `limit` (default 10, max 100). To see more issues, **narrow the time range** or increase `limit` up to the cap—there is no cursor/offset paging.

### List tools: three vs one

This package exposes **three** list tools (`list_app_vitals_crash_issues`, `list_app_vitals_anr_issues`, `list_app_vitals_nonfatal_issues`) for clearer agent discovery. A single tool with `issueKind` would shrink `tools/list` slightly; either pattern is a small refactor.

### Tool response shape

Successful distribution calls return JSON with **`ok: true`**. When there are no rows, **`empty: true`** and **`hint`** explain that it is a valid empty result, not a broken query. On failure, **`ok: false`** and **`error`** contain a concise message (including **403** / **401** / **400** context).

| Tool | Summary |
|------|--------|
| `list_app_vitals_crash_issues` | Top crash groups (`device.crash`). |
| `list_app_vitals_anr_issues` | Top ANR groups (`device.anr`). |
| `list_app_vitals_nonfatal_issues` | Top non-fatal groups (`non_fatal`). |
| `get_app_vitals_user_session_totals` | Unique users/sessions from `session.start` logs (denominators). |
| `get_app_vitals_issue_summary` | One-row summary for a `group_id`. |
| `get_app_vitals_issue_trend` | Time buckets; `trendView`: `aggregated` \| `appVersion` \| `os`. |
| `get_app_vitals_issue_stack_traces` | Sample exception rows with stack fields. |
| `get_app_vitals_issue_screen_breakdown` | Top screens by count for a `group_id`. |
| `get_app_vitals_exception_first_last_seen` | `min`/`max` timestamp per `group_id` over ~6 months; max **50** IDs per call. |

## Security

- Treat `PULSE_API_KEY` like a password; do not commit it.
- Revoking the key in the UI invalidates future exchanges; existing access tokens keep working until they expire (JWT lifetime).
- For teams, consider a hosted HTTP MCP with centrally managed auth instead of per-machine keys.

## Layout

- `src/index.ts` — requires env, exchanges API key, saves credentials, registers tools, **StdioServerTransport**
- `src/client.ts` — Axios client to Pulse API (wrapped `data` envelope; `raw` flag where needed); 401 → re-exchange
- `src/auth.ts` — load/save credentials file, `exchangeApiKeyForTokens`
- `src/tools/*.ts` — one module per domain (including `appVitals.ts`, `appVitalsHelpers.ts`, `appVitalsConstants.ts`)

## License

Same as the parent Pulse monorepo unless this package specifies otherwise.
