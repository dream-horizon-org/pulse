# EventCatalogJob

Enumerates all distinct event (custom + semantic) types seen per project for the `EventCatalog` UI screen.

Brief: [../../../components/spark-jobs.md](../../../components/spark-jobs.md) · Peers: [../../pulse-ui/screens/event-catalog](../../pulse-ui/screens/event-catalog.md), [../../backend-server/domains/eventdefinition](../../backend-server/domains/eventdefinition.md).

## Source location

- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/EventCatalogJob.java`
- Output consumed by `backend-server`'s `eventdefinition` domain.

## Public surface

```
spark-submit ... SparkJobRunner event-catalog --project-id=<pid> --start=<iso> --end=<iso>
```

## Internal design

1. Query ClickHouse: `SELECT DISTINCT PulseType, SpanName, LogAttributes['event.name'] FROM otel_{traces,logs} WHERE ProjectId=<pid> AND Timestamp BETWEEN <start> AND <end>`.
2. Merge with the pre-existing catalog (don't drop entries from previous runs).
3. Enrich each entry with: first-seen, last-seen, 7-day count, unique-user count.
4. Upsert to MySQL `event_catalog` table.

## Dependencies

- ClickHouse (source).
- MySQL (sink + prior-state read).

## Data contracts

Sink row: `(projectId, eventType, displayName, firstSeen, lastSeen, count7d, userCount7d, sourceKind)`. `sourceKind` ∈ `{system, custom}` — `custom` = came via Vector's custom-events path.

## Tests

Unit-level tests in `backend/spark/src/test/...` cover the upsert merge logic.

## History / decisions

Keeps a denormalized catalog so UI listing is cheap; refreshed nightly (schedule lives in the deploy cron / Airflow DAG — depends on env).

## Rebuild recipe

1. Query distinct events in window.
2. Read prior catalog from MySQL.
3. Merge; compute counts.
4. Upsert.
