# User Engagement

## Purpose

Engagement KPIs (DAU/WAU/MAU, session length, sticky users, returning
users) for the project.

## Source location

`pulse-ui/src/screens/UserEngagement/`.

## Routes

- `PROJECT_USER_ENGAGEMENT` -> `/projects/:projectId/user-engagement`

## Data fetched

- `useGetUserEngagementData` - aggregated engagement metrics (DAU,
  WAU, MAU, session length distribution).
- `useGetUserLastActiveToday` - today's active users tile.
- `useGetAppStats` - install/session counters.

All compose `useGetDataQuery` on `otel_traces` filtered by
`PulseType = 'session.start'` / `'session.end'`.

## State management

- `useFilterStore` - time range.
- `useSearchParams` - tab.

## Key UI components

- `Charts` (area + bar), Mantine `Tabs`, stat cards, `Sparkline`.

## Notable interactions

- Drill into a specific cohort opens the appropriate listing
  (sessions / users).

## Tests

`UserEngagement.test.tsx`.

## Rebuild recipe

1. Fetch engagement aggregates.
2. Render stat cards + time series.
3. Wire cross-links.
