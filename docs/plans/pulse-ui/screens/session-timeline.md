# Session Timeline

## Purpose

Chronological event timeline for a single session (`session.start` ->
`session.end`): screen loads, interactions, network calls, crashes,
custom events.

## Source location

`pulse-ui/src/screens/SessionTimeline/`.

## Routes

- `ROUTES.PROJECT_SESSION_TIMELINE` ->
  `/projects/:projectId/session/:id`

## Data fetched

- `useGetSessionData` - session header.
- `useGetSessionDetails` - ordered spans + logs (the timeline source).
- `useGetSpanDetails` - on-click span detail.
- `useGetRequestIdFromTime` - maps a time cursor to an http request id
  for cross-linking into Network Detail.

All inner queries hit `API_ROUTES.DATA_QUERY` with `SessionId =
:id`, ordered by `Timestamp`.

## State management

- `useParams.id`.
- `useSearchParams` - selected span id, filter chips (type),
  time-cursor.
- `useFilterStore` - inherited time range (capped to session window).

## Key UI components

- `PageHeader`, custom timeline lane, `Drawer` for span detail,
  `Sparkline`, `SessionCard` header, `QueryState`.

## Notable interactions

- Click span -> drawer with attributes.
- "Open in Network" / "Open in Screens" cross-links to those screens.
- Type filter chips toggle visibility per `pulse.type`.

## Tests

`SessionTimeline.test.tsx`.

## Rebuild recipe

1. Read `:id`; fetch header + details.
2. Group spans by lane (screen / interaction / network / event /
   crash).
3. Render lanes horizontally with hover tooltips.
4. Wire drawer to `useGetSpanDetails`.
