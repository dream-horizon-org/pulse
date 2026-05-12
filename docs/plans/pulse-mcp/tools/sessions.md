# pulse-mcp / sessions tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-mcp.md](../../../components/pulse-mcp.md).

## Purpose

List Pulse session replays filtered by interaction, device, event types, and
time window. AI clients use this to drill from an alert or crash group into the
concrete sessions that produced the signal.

## Source location

`pulse-mcp/src/tools/sessions.ts` — `registerSessionTools(server)`.

## Public surface

One tool today.

### `list_session_replays`

- Description: "List session replays for a project with optional interaction and time filters".
- Endpoint: `GET /v1/session-replays` with `X-Project-ID: {projectId}`.
- Params:

  | Param | Type | Default | Notes |
  |---|---|---|---|
  | `projectId` | string | — | required |
  | `interactionName` | string | — | optional filter |
  | `startTime` | string | — | ISO 8601 |
  | `endTime` | string | — | ISO 8601 |
  | `page` | int | `0` | zero-based |
  | `pageSize` | int | `20` | |
  | `device` | string | — | optional |
  | `eventTypes` | string[] | — | joined with `,` before send |

- Return: text content with the JSON-stringified replay list payload.

## Internal design

The handler forwards every named arg as a query parameter on the GET call.
`eventTypes` is the only field that needs reshaping: the wire contract is a
comma-separated string, so the handler does `eventTypes?.join(",")`.

## Dependencies

- `getClient().get(path, projectId, params)` — `PulseClient` from `src/client.ts`.
- `zod` for input validation.

## Data contracts

- Pagination: client-driven `page`/`pageSize`; the response shape matches the
  Pulse dashboard's replay list.
- `eventTypes` is the same enum the dashboard uses for replay filters.

## Tests

E2E via MCP host. No unit tests in this package.

## History / decisions

- Only `list_session_replays` is exposed today. A future `get_session_replay`
  tool (single-session detail + timeline) is a likely add but not yet wired.
- The header pattern (`projectId` second positional arg on `client.get`) is
  consistent with the rest of the tool surface so the same auth + tenancy path
  is exercised.

## Rebuild recipe

```ts
server.tool(
  "list_session_replays",
  "List session replays …",
  { projectId: z.string(), /* …filters… */ },
  async (args) => {
    const data = await getClient().get(
      "/v1/session-replays",
      args.projectId,
      { ...args, eventTypes: args.eventTypes?.join(",") },
    );
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  }
);
```
