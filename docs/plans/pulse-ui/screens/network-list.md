# Network List

## Purpose

Lists outbound HTTP endpoints for the project with call volume, error
rate, p50/p95 duration. Real-time over `otel_traces` with
`PulseType='http'`.

## Source location

`pulse-ui/src/screens/NetworkList/`.

## Routes

- `ROUTES.PROJECT_NETWORK_LIST` -> `/projects/:projectId/network-apis`

## Data fetched

`useGetDataQuery` composed inline against `API_ROUTES.DATA_QUERY`:

- `dataType: "TRACES"`
- `filters`: `PulseType = 'http'` + project + time range.
- `select`: URL/host, method, `COUNT()`, `countIf(StatusCode='ERROR')`,
  `quantile(0.5)(Duration)`, `quantile(0.95)(Duration)`,
  `uniq(SessionId)`.
- `groupBy`: URL/host + method.

## State management

- `useFilterStore` - time range.
- `useSearchParams` - search, method filter, sort key.

## Key UI components

- Mantine `Table`, `TextInput` (search), `Select` (method),
  `Pagination`, `Sparkline`, `QueryState`.

## Notable interactions

- Row click -> `PROJECT_NETWORK_DETAIL`
  (`/projects/:projectId/network-apis/:apiId`).
- Sort/search are client-side once page is loaded.

## Tests

`NetworkList.test.tsx`.

## Rebuild recipe

1. Build the request body in a `useMemo`.
2. Render a sortable table; map rows to `{ url, method, count,
   errorRate, p50, p95 }`.
3. Wire row navigation.
