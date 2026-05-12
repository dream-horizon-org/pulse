# JourneyComputeJob

Computes user-journey transition graphs from telemetry.

Brief: [../../../components/spark-jobs.md](../../../components/spark-jobs.md) · Peers: [funnel-compute](./funnel-compute.md).

## Source location

- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/JourneyComputeJob.java`
- Models: `JourneyDefinition`, `JourneyTransition` (under `.../model/`).
- I/O: `MysqlRepository`, `ClickHouseClient`.

## Public surface

```
spark-submit ... SparkJobRunner journey --project-id=<pid> --journey-id=<jid> --start=<iso> --end=<iso>
```

## Internal design

1. Load `JourneyDefinition` from MySQL (starting screen, termination screens, depth, filters).
2. Query ClickHouse for `(SessionId, ScreenName, Timestamp)` within window, filtered by project.
3. Group by session; produce adjacent pairs `(screenA → screenB)` with per-pair counts.
4. Aggregate into `JourneyTransition` rows weighted by session-count and unique-user-count.
5. Write to MySQL `journey_transitions` consumed by UI (`FunnelJourneyListing`, `CompareUserJourney`).

## Data contracts

Source signal: `pulse.type IN ('screen_load', 'screen_session')` in `otel_traces`.
Sink row: `(journeyId, projectId, fromScreen, toScreen, sessionCount, userCount, avgDwellMs)`.

## Tests

Shared with funnel tests under `backend/spark/src/test/...`.

## History / decisions

Chose directed-graph model (n-gram of 2) over full path enumeration — keeps cardinality bounded and renders cleanly in Sankey/UI.

## Rebuild recipe

1. Define `JourneyDefinition` + MySQL table.
2. Read screen events; partition by session; order by ts.
3. Emit transition pairs via `mapPartitions` / Spark SQL window functions.
4. Aggregate + write.
