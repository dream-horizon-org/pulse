# pulse-mcp — Plan index

Component brief: [`/docs/components/pulse-mcp.md`](../../components/pulse-mcp.md).

The MCP server is one TypeScript process. `src/index.ts` boots an `McpServer`,
authenticates with the Pulse REST API by exchanging `PULSE_API_KEY` for a JWT
pair, persists the credentials at `~/.pulse-mcp/credentials.json`, then registers
every tool group on a `StdioServerTransport`. Each tool is a thin wrapper around
one Pulse endpoint accessed via the shared `PulseClient` (axios + 401 re-exchange).

## Tool groups

| Group | Source | Plan page |
|---|---|---|
| Projects | `src/tools/projects.ts` | [tools/projects.md](tools/projects.md) |
| Metrics | `src/tools/metrics.ts` | [tools/metrics.md](tools/metrics.md) |
| Sessions | `src/tools/sessions.ts` | [tools/sessions.md](tools/sessions.md) |
| Alerts | `src/tools/alerts.ts` | [tools/alerts.md](tools/alerts.md) |
| Crashes / App Vitals | `src/tools/appVitals.ts` (+ helpers, constants) | [tools/crashes.md](tools/crashes.md) |

Additional tool groups exist in the source (`events.ts`, `funnels.ts`,
`journeys.ts`, `heatmap.ts`, `interactions.ts`, `query.ts`, `sdkConfig.ts`,
`anomaly.ts`, `appVitals.ts`) and follow the same pattern; only the five groups
above are required by the current plan.

## Shared building blocks

- `src/auth.ts` — `exchangeApiKeyForTokens(baseUrl, apiKey)` POSTs to
  `/v1/auth/api-key/exchange` and returns `{accessToken, refreshToken}`.
  `loadCredentials()` / `saveCredentials()` manage the on-disk cache.
- `src/client.ts` — `PulseClient` axios wrapper:
  - request interceptor: injects `Authorization: Bearer <accessToken>`,
    `Accept`/`Content-Type: application/json`;
  - response interceptor: on `401`, re-exchanges `PULSE_API_KEY` once and replays
    the request;
  - `get(path, projectId?, params?, raw?)` and `post(path, body, projectId?)`
    helpers; `projectId` is forwarded as `X-Project-ID`; `raw` skips the
    `{data: …}` unwrap used by most Pulse responses.
- Every tool returns
  `{ content: [{ type: "text", text: JSON.stringify(data, null, 2) }] }`.

## Rebuild recipe

```bash
cd pulse-mcp
npm install
npm run build           # tsc → dist/
PULSE_BASE_URL=http://localhost:8080 \
PULSE_API_KEY=pulse_mcp_… \
npm start
# stdio smoke test:
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | node dist/index.js
```
