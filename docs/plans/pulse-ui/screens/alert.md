# Alerts (Form + Wizard + Detail + Listing)

Covers `AlertForm`, `AlertFormWizard`, `AlertDetail`, `AlertListingPage`.

## Purpose

Manage alert rules: create (wizard), edit (form), browse (listing),
inspect evaluations and incidents (detail).

## Source locations

- `pulse-ui/src/screens/AlertListingPage/`
- `pulse-ui/src/screens/AlertFormWizard/` (exports `AlertForm` too)
- `pulse-ui/src/screens/AlertForm/`
- `pulse-ui/src/screens/AlertDetail/`

## Routes

- `PROJECT_ALERTS` -> `/projects/:projectId/alerts`
- `PROJECT_ALERT_DETAIL` -> `/projects/:projectId/alerts/:alertId`
- `PROJECT_ALERTS_FORM` ->
  `/projects/:projectId/configure-alert/*` (wizard sub-routes)

## Data fetched

- `useGetAlertList`, `useGetAlertFilters` - listing.
- `useGetAlertDetails`, `useGetAlertEvaluationHistory`,
  `useGetIncidents` - detail.
- `useGetAlertMetrics`, `useGetAlertScopes`, `useGetAlertSeverities`,
  `useGetAlertNotificationChannels`,
  `useGetNotificationChannelById` - form pickers.
- Mutations: `useCreateAlert`, `useUpdateAlert`, `useDeleteAlert`,
  `useSnoozeAlert`, `useResumeAlert`,
  `useCreateNotificationChannel`, `useUpdateNotificationChannel`,
  `useDeleteNotificationChannel`.

Backend lives in `backend/server/` + `backend/pulse-alerts-cron/`.

## State management

- `react-hook-form` for the wizard.
- `useSearchParams` for wizard step, listing tab/filter.
- `useFilterStore` for time range on detail charts.

## Key UI components

- `PageHeader`, `Tabs`, Mantine `Stepper`, `Select`, `MultiSelect`,
  `NumberInput`, `JsonInput`.
- `Charts` for evaluation history.
- `ConfirmationModal` for delete/snooze/resume.

## Notable interactions

- Wizard steps: metric -> condition -> scope/filters -> notification ->
  review.
- Listing supports snooze/resume inline.
- Detail tabs: Overview, Evaluation history, Incidents, Configuration.

## Tests

Per screen `*.test.tsx`. Mock all alert hooks.

## Rebuild recipe

1. Build listing first; wire `useGetAlertList` + search/sort.
2. Build wizard with `react-hook-form`; step state in
   `useSearchParams`.
3. Build detail with three tabs (overview, history, incidents).
4. Wire mutations with `queryClient.invalidateQueries` on
   `useGetAlertList` / `useGetAlertDetails`.
