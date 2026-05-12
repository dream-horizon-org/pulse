# pulse-mcp / metrics tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-mcp.md](../../../components/pulse-mcp.md).

## Purpose

Surface Pulse interaction metrics — APDEX, error rate, percentile latencies, and
user categorization — for a single interaction (`useCaseId`) over a time window,
filterable by app/OS/device/network/region.

## Source location

`pulse-mcp/src/tools/metrics.ts` — `registerMetricsTools(server)`.

## Public surface

Four POST-backed tools share the same input schema `MetricsParams`:

| Param | Type | Notes |
|---|---|---|
| `projectId` | string | Required, sent as `X-Project-ID`. |
| `interactionId` | string | Mapped to body field `useCaseId`. |
| `startTime`, `endTime` | string | ISO 8601. |
| `appVersion`, `platform`, `osVersion`, `networkProvider`, `deviceModel`, `state` | string | Optional filters; only included in body if defined (see `buildMetricsBody`). |

### `get_apdex_score`

- Description: APDEX time-series for an interaction.
- Endpoint: `POST /v3/metric/getApdexScore`.

### `get_error_rate`

- Description: error rate time-series for an interaction.
- Endpoint: `POST /v3/metric/getErrorRate`.

### `get_interaction_time`

- Description: percentile latencies (P50/P75/P90/P95/P99) for an interaction.
- Endpoint: `POST /v3/metric/composite/getInteractionTime`.

### `get_interaction_categorization`

- Description: user categorization breakdown (good / acceptable / poor).
- Endpoint: `POST /v3/metric/composite/getInteractionCategory`.

All four return `{ content: [{ type: "text", text: <pretty JSON> }] }`.

## Internal design

`buildMetricsBody(params)` constructs the request body in a single shape:

```ts
{ useCaseId, startTime, endTime, appVersion?, platform?, osVersion?,
  networkProvider?, deviceModel?, state? }
```

Each tool destructures `projectId` out of the args and forwards the rest:

```ts
await getClient().post("/v3/metric/getApdexScore", buildMetricsBody(rest), projectId);
```

The `PulseClient.post` helper attaches the `X-Project-ID` header and unwraps the
`{ data: … }` envelope.

## Dependencies

- `getClient().post` — see `src/client.ts`.
- `zod` for `MetricsParams` schema validation.

## Data contracts

- Body uses `useCaseId`, not `interactionId`. The MCP-level parameter is renamed
  for clarity but the wire contract stays canonical to pulse-server.
- All time-range and dimensional filter semantics match pulse-server `/v3/metric/*`.

## Tests

End-to-end only (no per-tool unit tests in this package).

## History / decisions

- The `v3` metric routes are kept as-is — they're the same paths the dashboard
  hits (`pulse-ui`).
- `composite/*` variants return Apdex, latency, errors, and categorization in
  one response; the dashboard uses them for the single-interaction detail page.

## Rebuild recipe

Re-implement `registerMetricsTools` by:
1. Declaring the shared `MetricsParams` `zod` schema.
2. Writing `buildMetricsBody` to whitelist defined-only fields.
3. Looping the four `server.tool(...)` registrations with the right endpoint URL.
4. Wiring the export into `src/index.ts`.
