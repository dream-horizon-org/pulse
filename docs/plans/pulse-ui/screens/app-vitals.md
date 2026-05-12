# App Vitals

## Purpose

Crash + ANR + non-fatal grouping. Lists issue groups, drills into a
group then into a single occurrence with stack trace.

## Source location

`pulse-ui/src/screens/AppVitals/` (exports `AppVitals`, `IssueDetail`,
`OccurrenceDetail`).

## Routes

- `PROJECT_APP_VITALS` -> `/projects/:projectId/app-vitals`
- `PROJECT_APP_VITALS_ISSUE_DETAIL` ->
  `/projects/:projectId/app-vitals/:groupId`
- `PROJECT_APP_VITALS_OCCURRENCE_DETAIL` ->
  `/projects/:projectId/app-vitals/:issueId/occurrence/:occurrenceId`

## Data fetched

- `useGetErrorRate` + `useCachedErrorRate` - top-line rate.
- `useGetErrorAttribution` - per-group attribution.
- `useGetAppStats` - install/session counters for context.
- Inline `useGetDataQuery` against `stack_trace_events` /
  `otel_logs` with `pulse.type IN ('device.crash','non_fatal')` for
  group listings and occurrence rows.
- `useGetSpanDetails` for the occurrence drawer.

## State management

- `useFilterStore` - time range.
- `useParams` - `groupId`, `issueId`, `occurrenceId`.
- `useSearchParams` - status filter (open/resolved), platform, version.

## Key UI components

- `PageHeader`, `Charts` (rate + new-vs-recurring), Mantine `Table`,
  stack-trace viewer (component under
  `src/screens/AppVitals/components/`), `Drawer`.

## Notable interactions

- Group row click -> issue detail.
- Occurrence row click -> occurrence detail with frame-by-frame stack
  trace + breadcrumbs.

## Tests

`AppVitals.test.tsx`, `IssueDetail.test.tsx`,
`OccurrenceDetail.test.tsx`.

## Rebuild recipe

1. Build group list (`useGetDataQuery`, groupBy fingerprint).
2. Issue detail: aggregate occurrences + chart over time.
3. Occurrence: render symbolicated stack + breadcrumbs + spans.
