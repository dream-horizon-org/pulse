# Funnels & Journeys (Create + Detail + Listing + Compare)

Covers `FunnelJourneyCreate`, `FunnelJourneyDetail`,
`FunnelJourneyListing`, `CompareUserJourney`.

## Purpose

Define multi-step funnels and free-form user journeys; analyse
conversion, drop-off and step latency; compare cohorts.

## Source locations

- `pulse-ui/src/screens/FunnelJourneyListing/` (exports `FunnelsList`,
  `JourneysList`).
- `pulse-ui/src/screens/FunnelJourneyCreate/` (exports `CreateFunnel`,
  `CreateJourney`).
- `pulse-ui/src/screens/FunnelJourneyDetail/` (exports `FunnelDetail`,
  `JourneyDetail`).
- `pulse-ui/src/screens/CompareUserJourney/`.

## Routes

- `FUNNELS_LIST` -> `/projects/:projectId/funnels`
- `FUNNELS_CREATE` -> `/projects/:projectId/funnels/create`
- `FUNNEL_DETAIL` -> `/projects/:projectId/funnels/:funnelId`
- `JOURNEYS_LIST` -> `/projects/:projectId/journeys`
- `JOURNEYS_CREATE` -> `/projects/:projectId/journeys/create`
- `JOURNEY_DETAIL` -> `/projects/:projectId/journeys/:journeyId`

## Data fetched

Funnels:

- `useGetFunnelsList`, `useGetFunnelDetail`, `useGetFunnelData`,
  `useCreateFunnel`, `useUpdateFunnel`, `useDeleteFunnel`,
  `useStopFunnel`.

Journeys:

- `useGetJourneysList`, `useGetJourneyDetail`, `useCreateJourney`,
  `useUpdateJourney`, `useDeleteJourney`, `useStopJourney`.

Compare:

- Two parallel `useGetFunnelData` / `useGetJourneyDetail` calls
  diff-rendered side-by-side.

## State management

- `react-hook-form` for create.
- `useSearchParams` - tab, compare-against id.
- `useFilterStore` - time range on detail.

## Key UI components

- Mantine `Stepper`, `Select`, `MultiSelect`, `Group`, `Table`.
- `Charts` for conversion funnel + drop-off waterfall.
- `ConfirmationModal`.

## Notable interactions

- "Stop" puts a funnel into paused state without deleting it.
- Compare screen overlays two funnel/journey datasets with a delta
  column.

## Tests

`*.test.tsx` per screen.

## Rebuild recipe

1. Listing first; wire create CTA.
2. Create with stepper; step definitions stored as JSON.
3. Detail: funnel chart + drop-off table + per-step duration histogram.
4. Compare: pick second funnel/journey from listing.
