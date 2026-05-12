# Screen List

## Purpose

Lists the top screens for a project with per-screen metrics: time spent,
load time, load count, unique users, error count, unique sessions. Page
is **fully real-time** - no precomputed analytics tables. Cost scales
with time range x project traffic.

## Source location

`pulse-ui/src/screens/ScreenList/`.

## Routes

- `ROUTES.PROJECT_SCREENS` -> `/projects/:projectId/screens`

## Data fetched

Two real-time `useGetDataQuery` calls (both `dataType: "TRACES"`):

### Query 1 - `useGetScreenNames` (top screens)

`src/hooks/useGetScreenNames/useGetScreenNames.ts`:

- `select`:
  - `COL(SCREEN_NAME)` AS `screen_name`
  - `CUSTOM("COUNT()")` AS `screen_count`
- `groupBy: ["screen_name"]`
- `orderBy: [{ field: "screen_count", direction: "DESC" }]`
- `filters`:
  - `PulseType IN ('screen_session','screen_load')`
  - optional `SCREEN_NAME LIKE '%<searchStr>%'` when searching.
- `limit`: `15` normally, `100` when a search string is present.
- After the response, the list is filtered client-side by `searchStr`
  (lower-cased `includes`) because the API may not honour `LIKE`.

### Query 2 - `useGetScreenDetails` (per-screen metrics)

`src/hooks/useGetScreenDetails/useGetScreenDetails.ts` keyed on the
screen names returned by Query 1:

- `select`:
  - `COL(SCREEN_NAME)` AS `screen_name`
  - `sumIf(Duration, PulseType='screen_session')` AS
    `total_time_spent`
  - `sumIf(Duration, PulseType='screen_load')` AS `total_load_time`
  - `countIf(PulseType='screen_load')` AS `load_count`
  - `uniq(nullIf(InstallationId,''))` AS `user_count`
  - `countIf(StatusCode!='ERROR')` AS `success_count`
  - `countIf(StatusCode='ERROR')` AS `error_count`
  - `COUNT()` AS `screen_count`
  - `uniq(nullIf(SessionId,''))` AS `unique_session_count`
- `groupBy: ["screen_name"]`
- `filters`:
  - `SCREEN_NAME IN (<names from query 1>)`
  - `PulseType IN ('screen_session','screen_load')`

Column names come from `src/constants/PulseOtelSemcov.ts`
(`COLUMN_NAME.SCREEN_NAME`, `COLUMN_NAME.INSTALLATION_ID`,
`COLUMN_NAME.SESSION_ID`).

Both queries hit `backend/server/` `API_ROUTES.DATA_QUERY`, which proxies
to ClickHouse `otel.otel_traces`. Cost scales with time range x project
traffic.

## State management

- `useFilterStore` - global time range.
- Local component state for `searchStr` (debounced).
- `useSearchParams` for time-range share links if used.

## Key UI components

- `PageHeader`, `QueryState`, `ErrorAndEmptyState`.
- Mantine `Table`, `TextInput` (search), `Pagination` (client-side).
- `Sparkline` per row (optional).

## Notable interactions

- Search debounces and triggers Query 1 with `limit: 100`.
- Row click navigates to `PROJECT_SCREEN_DETAILS`
  (`/projects/:projectId/screens/:screenName`).
- Both queries refetch on time-range change.

## Tests

`src/screens/ScreenList/ScreenList.test.tsx`. Mock both hooks at
module boundary; verify search filter, row navigation, empty state.

## Rebuild recipe

1. Build `useGetScreenNames` returning `{ screenNames, isLoading,
   isError }`.
2. Build `useGetScreenDetails` keyed on `screenNames`; merge with
   `useMemo`.
3. Render a table with derived metrics (load avg, error rate).
4. Wire search box -> hook input.
5. Add row click -> `navigate` to `PROJECT_SCREEN_DETAILS`.
