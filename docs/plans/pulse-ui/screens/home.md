# Home

## Purpose

Landing dashboard for a project. Shows top-line health (Apdex, error
rate, active sessions, app stats) plus quick-access tiles into screens,
interactions, alerts, and AI chat.

## Source location

`pulse-ui/src/screens/Home/`.

## Routes

- `ROUTES.PROJECT_DASHBOARD` -> `/projects/:projectId`

## Data fetched

- `useGetAppStats` - aggregate session / install / crash counters.
- `useGetApdexScore` + `useGetCachedApdexScore` - apdex (live + cached).
- `useGetErrorRate` + `useCachedErrorRate` - error rate over the range.
- `useGetActiveSessionsData` - live active session count.
- `useGetIncidents` - open incidents card.
- `useGetUserLastActiveToday` - greeting card.

All numeric hooks compose `useGetDataQuery` against `API_ROUTES.DATA_QUERY`
filtered by `ProjectId` + the global time range from `useFilterStore`.

## State management

- `useFilterStore` - time range.
- `ProjectContext` - `projectId`.
- `useSearchParams` - none on Home; navigation drives subsequent routes.

## Key UI components

- `PageHeader`, `Layout`, `Navbar`, `Header`.
- `Charts` (sparkline + area), `Sparkline`.
- `SessionCard` for live sessions list.
- `StatsSkeleton`, `GraphSkeleton`, `ErrorAndEmptyState`.

## Notable interactions

- Time-range picker dispatches into `useFilterStore`; all queries
  refetch on key change.
- Clicking a stat tile navigates to the corresponding screen
  (`PROJECT_APP_VITALS`, `PROJECT_INTERACTIONS`, etc.).
- Active sessions tile auto-refreshes (`refetchInterval` on
  `useGetActiveSessionsData`).

## Tests

`src/screens/Home/Home.test.tsx` with `makeRequest` mocked at
`src/helpers/makeRequest`. Wrap in `MantineProvider` + a stub
`ProjectContext`.

## Rebuild recipe

1. `Home/index.ts` + `Home.tsx` + `Home.module.css`.
2. Compose: `PageHeader`, four stat cards, `Charts` panel, live
   sessions strip, incidents card, AI suggestion strip.
3. Wire each card to its hook with `QueryState` and skeletons.
