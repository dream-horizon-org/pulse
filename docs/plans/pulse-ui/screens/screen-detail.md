# Screen Detail

## Purpose

Per-screen drilldown: load-time distribution, error attribution, RCA
narrative, top problematic interactions on that screen, heatmap.

## Source location

`pulse-ui/src/screens/ScreenDetail/`.

## Routes

- `ROUTES.PROJECT_SCREEN_DETAILS` ->
  `/projects/:projectId/screens/:screenName`

## Data fetched

- `useGetScreenDetails` - same shape as ScreenList but filtered to one
  screen (`SCREEN_NAME IN [:screenName]`).
- `useGetScreensHealthData` - time series for load/session duration +
  error rate, grouped by time bucket.
- `useGetScreenRootCause` (`GET_SCREEN_ROOT_CAUSE_ROUTE` in
  `src/constants/API.ts`) - structured RCA payload.
- `useGetScreenRcaNarrative` (`POST_SCREEN_RCA_NARRATIVE_ROUTE`) - AI
  narrative on top of the RCA payload.
- `useRegenerateScreenRcaNarrative` - mutation to regenerate.
- `useGetScreenNameToEventQueryMapping` - resolves event names tied to
  this screen for the AI prompt.
- `useHeatmapData` + `useHeatmapFromActiveConfig` - tap heatmap (gated
  on SDK config).

## State management

- `useFilterStore` - time range.
- `useSearchParams` - active tab (`overview`, `errors`, `heatmap`,
  `rca`), occurrence id.
- `useParams` - `screenName`.

## Key UI components

- `PageHeader`, `Tabs` (Mantine), `Charts`, `Sparkline`,
  `MarkdownContent` for the AI narrative, `ErrorAndEmptyState`.

## Notable interactions

- Tab change writes to `useSearchParams`.
- "Regenerate" button on RCA tab fires
  `useRegenerateScreenRcaNarrative`.
- Heatmap tab hidden if `useHeatmapFromActiveConfig` reports off.

## Tests

`ScreenDetail.test.tsx` covering tab routing, RCA regenerate, empty
states.

## Rebuild recipe

1. Read `screenName` from `useParams`.
2. Compose tabs: Overview (`useGetScreenDetails` +
   `useGetScreensHealthData`), Errors, Heatmap, RCA.
3. RCA tab: fetch `useGetScreenRootCause` -> render structured tree;
   fetch `useGetScreenRcaNarrative` -> `MarkdownContent`; expose
   Regenerate button.
