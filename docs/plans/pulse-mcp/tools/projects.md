# pulse-mcp / projects tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-mcp.md](../../../components/pulse-mcp.md).

## Purpose

Expose Pulse project metadata: which projects the authenticated user belongs to,
project detail, and project membership. These are the first calls any AI client
makes to bootstrap `projectId` for every other tool.

## Source location

`pulse-mcp/src/tools/projects.ts` — `registerProjectTools(server)`.

## Public surface

Three tools, all read-only.

### `list_projects`

- Description: "List all Pulse projects accessible to the authenticated user".
- Params: none.
- Endpoint: `GET /v1/users/me/projects`.
- Return: text content with JSON-stringified `data` array of projects.

### `get_project`

- Description: "Get details of a specific Pulse project".
- Params: `{ projectId: string }`.
- Endpoint: `GET /v1/projects/{projectId}` with `X-Project-ID: {projectId}`.
- Return: project detail object.

### `list_project_members`

- Description: "List members of a Pulse project with their roles".
- Params: `{ projectId: string }`.
- Endpoint: `GET /v1/projects/{projectId}/members` with `X-Project-ID: {projectId}`.
- Return: array of `{userId, role, …}` records.

## Internal design

Each tool is a single `server.tool(name, description, zodSchema, handler)` call.
The handler awaits `getClient().get(...)` and serialises the result. There is no
caching, transformation, or pagination.

## Dependencies

- `@modelcontextprotocol/sdk/server/mcp.js` — `McpServer`.
- `zod` — schema validation.
- `../client.js` — `getClient()` returning a singleton `PulseClient`.

## Data contracts

- Input: validated by `zod` (`projectId: z.string()`).
- Output: standard MCP `content: [{type:"text", text}]`. The `text` is a
  pretty-printed JSON dump of the `data` field returned by Pulse.

## Tests

No standalone tool unit tests; coverage is end-to-end via MCP host smoke tests
(`tools/list` then `tools/call`).

## History / decisions

- Picked `GET /v1/users/me/projects` (not `/v1/projects`) so the API enforces the
  caller's project membership server-side.
- `X-Project-ID` header is set on `get_project` even though the path also carries
  `{projectId}` — this matches pulse-server's row-policy enforcement convention.

## Rebuild recipe

```ts
// src/tools/projects.ts (sketch)
server.tool("list_projects", "List …", {}, async () => {
  const data = await getClient().get<unknown>("/v1/users/me/projects");
  return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
});
```

Re-register from `src/index.ts` via `registerProjectTools(server)`.
