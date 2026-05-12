# Critical Interactions (List + Form + Details)

Covers `CriticalInteractionList`, `CriticalInteractionForm`,
`CriticalInteractionDetails`.

## Purpose

Define and monitor critical user interactions (e.g. "Add to Cart",
"Checkout"). Tracks latency, success rate, error attribution, RCA.

## Source locations

- `pulse-ui/src/screens/CriticalInteractionList/`
- `pulse-ui/src/screens/CriticalInteractionForm/`
- `pulse-ui/src/screens/CriticalInteractionDetails/`

## Routes

- `PROJECT_INTERACTIONS` -> `/projects/:projectId/interactions`
- `PROJECT_INTERACTION_FORM` ->
  `/projects/:projectId/critical-interaction-form/*`
- `PROJECT_INTERACTION_DETAILS` ->
  `/projects/:projectId/interaction-details/*`
- `PROJECT_ALL_INTERACTION_DETAILS` ->
  `/projects/:projectId/user-experience`

## Data fetched

- `useGetInteractions`, `useGetInteractionListFilters`,
  `useGetSuggestedInteractions`,
  `useGetProblematicInteractions`,
  `useGetProblematicInteractionsStats`,
  `useGetTopInteractionsHealthData`.
- Details: `useGetInteractionDetails`,
  `useGetInteractionDetailsGraphs`, `useGetInteractionTime`,
  `useGetErrorAttribution`.
- Form: `makeCriticalInteractionFormRequestBody`,
  `makeCriticalInteractionFormDataUsingJobDetails`,
  `useGetJobStatus` (background config job),
  `useUpdateInteraction`, `useDeleteInteraction`.
- RCA: `GET_RCA_STATUS_ROUTE`, `POST_RCA_REPORT_ROUTE`,
  `GET_RCA_JOB_ROUTE` (leaf API routes from `constants/API.ts`) via
  `useGetRcaReport`, `useRegenerateRcaReport`.

## State management

- `useFilterStore` - time range + critical-interaction filters
  (`PLATFORM`, `APP_VERSION`, `NETWORK_PROVIDER`, `STATE`,
  `OS_VERSION`).
- `react-hook-form` for the multi-step form.
- `useSearchParams` for tab, occurrence id.

## Key UI components

- `Layout`, `PageHeader`, `Tabs`, `Charts`, `ErrorAndEmptyState`,
  `MarkdownContent` (RCA narrative), Mantine `Stepper` (form),
  `ConfirmationModal`.

## Notable interactions

- Form runs an async job; UI polls `useGetJobStatus` until ready.
- Details RCA card supports regenerate (clears cached report).
- Suggested-interactions feed proposes candidates the user can promote.

## Tests

Three `*.test.tsx` files with full hook mocks.

## Rebuild recipe

1. List screen first; reuse `useFilterStore` filters.
2. Form: stepper + `react-hook-form`; background job polling.
3. Details: tabs (Overview, Errors, RCA, Sessions); RCA tab consumes
   leaf API routes.
