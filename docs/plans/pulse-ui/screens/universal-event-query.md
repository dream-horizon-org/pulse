# Universal Event Query

## Purpose

Product-facing universal query surface: pick a telemetry table,
filter, group, and run async queries with history.

## Source location

`pulse-ui/src/screens/UniversalEventQuery/`
(component file `UniversalEventQuery.tsx`).

## Routes

- `PROJECT_UNIVERSAL_QUERYING` ->
  `/projects/:projectId/universal-querying`

## Data fetched

- `useUniversalQueryTables` - list of queryable tables.
- `useUniversalQueryTableColumns` - per-table columns.
- `useGetEventProps` - event property metadata.
- `useGetTelemetryFilters`, `useGetDashboardFilters` - reusable
  filters.
- `useValidateUniversalQuery` - server-side validation.
- `useSubmitQuery` - submit (returns `queryId`).
- `useRunUniversalQuery` - convenience to submit + poll.
- `useGetQueryHistory` - past queries (per project / per user).
- `useQueryResultFromQueryId`, `useQueryResultFromQueryId_diff` -
  poll results / diff two runs.
- `useCancelQuery` - cancel running query.

## State management

- `react-hook-form` for the builder.
- `useSearchParams` - active `queryId`, tab.
- `useFilterStore` - global time range.

## Key UI components

- Mantine `Tabs` (Builder / Results / History), table picker, filter
  rows, `JsonInput` for advanced mode, results `Table`, history list,
  `ConfirmationModal`.

## Notable interactions

- Submit -> show running state -> poll until ready or canceled.
- "Diff with previous" uses `_diff` hook.
- History rows re-hydrate the builder.

## Tests

`UniversalEventQuery.test.tsx`.

## Rebuild recipe

1. Builder reads tables/columns hooks.
2. Validation gates Submit.
3. Submit + poll loop driven by `queryId` in URL.
4. History/diff tabs.
