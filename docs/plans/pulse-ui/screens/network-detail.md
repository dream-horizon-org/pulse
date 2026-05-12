# Network Detail

## Purpose

Per-endpoint drilldown: request volume time series, error breakdown by
status code, slowest requests, sample sessions.

## Source location

`pulse-ui/src/screens/NetworkDetail/`.

## Routes

- `ROUTES.PROJECT_NETWORK_DETAIL` ->
  `/projects/:projectId/network-apis/:apiId`

## Data fetched

Inline `useGetDataQuery` calls, all `dataType: "TRACES"` with
`PulseType='http'`:

- Time series: `COUNT()` per time bucket; `countIf(StatusCode='ERROR')`.
- Status code breakdown: `groupBy: ["StatusCode"]` or
  `["http.response.status_code"]`.
- Top sessions: `groupBy: [SessionId]`, sorted by `COUNT()` desc.
- Sample errors: filtered by `StatusCode='ERROR'` with `limit: 50`.

Span detail fetched via `useGetSpanDetails` when a row is opened.

## State management

- `useFilterStore` - time range.
- `useSearchParams` - tab + selected span id.
- `useParams.apiId`.

## Key UI components

- `PageHeader`, `Tabs`, `Charts`, Mantine `Drawer` for span detail,
  `QueryState`.

## Notable interactions

- Selecting a row opens a drawer with the span attributes (response
  body excluded by SDK by default).
- "Open session" jumps to `PROJECT_SESSION_TIMELINE`.

## Tests

`NetworkDetail.test.tsx`.

## Rebuild recipe

1. Resolve `apiId` from params; reverse-map to URL/method.
2. Compose four panels (time series, status breakdown, top sessions,
   error sample) each with its own `useGetDataQuery`.
3. Span drawer uses `useGetSpanDetails`.
