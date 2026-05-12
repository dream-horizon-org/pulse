# Event Catalog

## Purpose

Browse the catalogue of events emitted by the SDKs in the project: name,
sample count, sample properties, last-seen.

## Source location

`pulse-ui/src/screens/EventCatalog/`.

## Routes

- `PROJECT_EVENT_CATALOG` -> `/projects/:projectId/event-catalog`

## Data fetched

- Inline `useGetDataQuery` against `otel_logs`/`otel_traces` filtered
  by `pulse.type` enumerations with `groupBy: ["event.name"]`.
- `useGetEventProps` - sample properties per event name.
- `useGetUserEvents` - cross-reference with whitelist
  (`helpers/whitelistEvents`).

## State management

- `useFilterStore` - time range.
- `useSearchParams` - search, selected event for drawer.

## Key UI components

- Mantine `Table`, `TextInput`, `Drawer` for properties, `Badge` for
  category, `QueryState`.

## Notable interactions

- Row click -> drawer with sampled property values + last-seen time.
- "Add to whitelist" toggles via `helpers/whitelistEvents`.

## Tests

`EventCatalog.test.tsx`.

## Rebuild recipe

1. Aggregate count per event name.
2. Render searchable table.
3. Drawer fetches `useGetEventProps`.
