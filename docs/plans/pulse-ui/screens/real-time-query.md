# Real Time Query

## Purpose

Ad-hoc real-time querying surface (developer-facing). Build a
ClickHouse-style query, run it against the live data plane, view rows
+ runtime metadata.

## Source location

`pulse-ui/src/screens/RealTimeQuery/`.

## Routes

Reached via internal/developer surfaces; no top-level navbar entry.
(`PROJECT_UNIVERSAL_QUERYING` is the production-facing variant; see
[`universal-event-query.md`](universal-event-query.md).)

## Data fetched

- `useGetDataQuery` directly with a user-built request body.
- `useQueryMetadata`, `useQueryStats`, `useQueryError` enrich the
  result panel (duration, scanned rows, error info).

## State management

- Local component state for the query builder (dataType, select,
  filters, groupBy, orderBy, limit).
- `useFilterStore` - time range.
- `useSearchParams` - serialised query for share links.

## Key UI components

- Mantine `JsonInput` / structured builder, `Table` for results,
  `Code` block for the generated SQL/payload, `QueryState`.

## Notable interactions

- Run button hits `useGetDataQuery` with `enabled` toggled true.
- Copy-as-curl / share URL.

## Tests

`RealTimeQuery.test.tsx`.

## Rebuild recipe

1. Build a JSON editor + structured builder backed by the same shape
   `useGetDataQuery` expects.
2. Render results with paginated table + metadata panel.
