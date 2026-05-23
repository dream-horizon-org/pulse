# pulse-mcp

A read-only [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server for Pulse (mobile + web observability): it exposes Pulse HTTP APIs as MCP tools so assistants can list projects, inspect metrics, sessions, alerts, heatmaps, funnels, journeys, events, interactions, SDK config, and **App Vitals** (crashes, ANRs, non-fatal issues, issue detail).

**Default transport:** **stdio** (local process). An optional **HTTP** entrypoint (`dist/index-http.js`) exists for evals and hosted deployments. Node **`^22.22.0`** required ([`package.json` `engines`](package.json); [`.nvmrc`](.nvmrc) pins `v22.22.0`).

## Dynamic tool loading

Domain tools are **not** all registered at startup. On connect, `tools/list` returns only the **core** tools plus two meta-tools:

| Tool | Purpose |
|------|---------|
| `list_projects` | List accessible projects |
| `get_project` | Project details |
| `list_project_members` | Project members and roles |
| `register_tools` | Unlock one or more tool categories for this session |
| `reset_tools` | Remove dynamically registered categories; keep core tools only |

Call **`register_tools`** with the categories relevant to the user's request, then use the unlocked domain tools. Available categories:

| Category | Unlocks |
|----------|---------|
| `crashes` | App Vitals — crash/ANR/non-fatal lists, issue detail, trends, stack traces |
| `sessions` | Session replay listing |
| `interactions` | Critical interactions, RCA, APDEX, error rate, response time |
| `events` | Event catalog, categories, search |
| `funnels` | Funnel list, detail, tags, builder events |
| `journeys` | User journey flows |
| `alerts` | Alert rules, evaluation history, notification channels |
| `heatmap` | Touch heatmap data |
| `sdk` | SDK configuration and rules |

Example: before listing crash issues, call `register_tools` with `categories: ["crashes"]`, then call `list_app_vitals_crash_issues`. Use `reset_tools` to drop unlocked categories and start fresh.

Assistants connected via Cursor or Claude Desktop must follow this flow — the tool schema alone does not expose domain tools until `register_tools` runs.

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

Dependencies are tracked in **`yarn.lock`**. Install with **Yarn 4** — [`package.json`](package.json) pins the release via **`packageManager`** (`yarn@4.12.0`). Corepack (`corepack enable`) is the usual way to get that Yarn on PATH.

Production bundle: **`yarn build`** runs [**tsup**](https://github.com/egoist/tsup) (esbuild) and writes **`dist/index.js`** and **`dist/index-http.js`** (+ source maps). **`yarn typecheck`** runs **`tsc --noEmit`** only (no emit).

```bash
cd pulse-mcp
yarn install
yarn build
```

| Script | Description |
|--------|-------------|
| `yarn dev` | Watch mode (**tsup** `--watch`) |
| `yarn start` | Stdio MCP server (`node dist/index.js`) |
| `yarn start:http` | HTTP MCP server on `PORT` (default **3001**); endpoints `/mcp` and `/reset` |
| `yarn typecheck` | Typecheck only (`tsc --noEmit`) |
| `yarn promptfoo:eval` | Build + Promptfoo eval (stdio MCP via ADK provider) |
| `yarn promptfoo:eval:http` | Build + Promptfoo eval (HTTP MCP; run `yarn start:http` first) |
| `yarn promptfoo:view` | Open Promptfoo results UI |

## Quick smoke test (stdio)

After build:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | PULSE_BASE_URL=http://localhost:8080 \
    PULSE_API_KEY="pulse_mcp_YOUR_KEY" \
    node dist/index.js
```

You should see stderr lines such as `Exchanging API key for tokens...`, `Authentication successful.`, `Pulse MCP server running on stdio`, then a JSON `result.tools` array on stdout with **five** tools (`list_projects`, `get_project`, `list_project_members`, `register_tools`, `reset_tools`). Domain tools appear only after `register_tools` is called in a live session.

## Eval tooling (optional)

**MCP Inspector — manual lane.** Use it to explore `tools/list`, schemas, and live calls against Pulse while developing the server.

**Promptfoo — NL tool-selection eval.** Config lives under [`evals/promptfoo/`](evals/promptfoo/). **`yarn promptfoo:eval`** builds the server, then runs evals via the Google ADK provider ([`evals/promptfoo/providers/gemini-adk-agent.mjs`](evals/promptfoo/providers/gemini-adk-agent.mjs)), which connects to **`dist/index.js`** over MCP stdio and discovers tools live from `tools/list` — no generated tool schema file.

- **Build:** `yarn promptfoo:eval` runs `yarn build` first (needs a reachable Pulse API for API-key exchange — same as starting MCP normally).
- **Pulse API:** set `PULSE_BASE_URL` and `PULSE_API_KEY` (passed to the MCP server subprocess).
- **Model:** `GOOGLE_API_KEY` or `GEMINI_API_KEY` (Google AI Studio).
- **Node:** same as [`package.json` `engines`](package.json) — **`promptfoo` enforces this** when you run `yarn promptfoo:eval`.
- **HTTP transport eval** (dynamic `register_tools` flow): start `yarn start:http` in one terminal, then `yarn promptfoo:eval:http`.

Additional cases live in tracked YAML under `evals/promptfoo/tests/`.

```bash
cd pulse-mcp
yarn install
export GOOGLE_API_KEY="your_gemini_key"
export PULSE_BASE_URL="http://localhost:8080"
export PULSE_API_KEY="pulse_mcp_your_personal_token"
yarn promptfoo:eval
```

## Cursor / Claude Desktop

Point `command`/`args` at `dist/index.js` and pass env (example for Cursor: `~/.cursor/mcp.json` or project `.cursor/mcp.json`). The assistant must call **`register_tools`** to unlock domain tools before using them (see [Dynamic tool loading](#dynamic-tool-loading)).

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

For a manual end-to-end check: create a key at **`/account/tokens`**, point this MCP at your `PULSE_BASE_URL`, set `PULSE_API_KEY`, run the smoke command above, then call `register_tools` (e.g. with `categories: ["crashes"]`) before domain tools. Revoke the key in the UI and confirm a new exchange fails until you create a new key.

## App Vitals tools

These tools are in the **`crashes`** category — call `register_tools` with `categories: ["crashes"]` before using them.

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

- `tsup.config.ts` — production bundle (**esbuild** via **tsup**) → **`dist/index.js`**, **`dist/index-http.js`**
- `src/index.ts` — stdio entry: env + API-key exchange, core tools + `register_tools` / `reset_tools`, **StdioServerTransport**
- `src/index-http.ts` — HTTP entry: Streamable HTTP on `/mcp`, session state reset on `/reset` (eval isolation)
- `src/client.ts` — Axios client to Pulse API (wrapped `data` envelope; optional unwrapped **`get`** for `/v1/configs/active`); 401 → re-exchange
- `src/auth.ts` — load/save credentials file, `exchangeApiKeyForTokens`
- `src/jwtEmail.ts` — read `email` claim from JWT (session listing `user-email` header)
- `src/timeBucket.ts` — time-series bucket sizing (mirrors pulse-ui `TimeBucketUtil`)
- `src/tools/register.ts` — dynamic category registration (`register_tools`, `reset_tools`)
- `src/tools/*.ts` — one module per domain (including `appVitals.ts`, `appVitalsHelpers.ts`, `appVitalsConstants.ts`)
- `evals/promptfoo/` — Promptfoo configs, ADK provider, NL tool-selection test suites

## License

Same as the parent Pulse monorepo unless this package specifies otherwise.
