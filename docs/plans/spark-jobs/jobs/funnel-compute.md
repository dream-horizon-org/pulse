# FunnelComputeJob

Evaluates user-defined funnels (ordered step sequences) against telemetry, writes per-step conversion counts.

Brief: [../../../components/spark-jobs.md](../../../components/spark-jobs.md) · Peers: [journey-compute](./journey-compute.md), [event-catalog](./event-catalog.md).

## Purpose

For each funnel definition stored in MySQL, compute how many unique users completed each consecutive step within a user-defined window. Feeds `pulse-ui` `FunnelJourneyDetail` and `CompareUserJourney` screens.

## Source location

- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/FunnelComputeJob.java`
- Models: `FunnelDefinition`, `FunnelStep`, `FunnelFilter`, `FunnelResult` (under `.../model/`).
- `FilterFieldMapper.java` — UI field → SQL column mapping.
- `FunnelFilterOperators.java` — operator translation (`EQ`, `IN`, `LIKE`, etc.).

## Public surface

CLI (via `SparkJobRunner`):
```
spark-submit ... SparkJobRunner funnel --project-id=<pid> --funnel-id=<fid> --start=<iso> --end=<iso>
```

## Internal design

1. Read `FunnelDefinition` from MySQL via `MysqlRepository`.
2. For each step, push down a ClickHouse query via `ClickHouseClient` that returns `(UserId, Timestamp)` rows matching that step's filters.
3. Join successive steps in Spark with an ordered-event check (step N+1 timestamp > step N timestamp, within `windowMs`).
4. Aggregate `UNIQUE(UserId)` per step → `FunnelResult`.
5. Write results to MySQL funnel results table consumed by `backend-server` (`resources/productAnalysis/`).

## Dependencies

- Spark core, Spark SQL.
- ClickHouse JDBC (for source reads).
- MySQL JDBC (for defs + results).
- AWS Secrets Manager (via `AwsSecretsHelper`).

## Data contracts

- Source: `otel_traces` / `otel_logs` in ClickHouse, filtered by `ProjectId` + time + step predicates.
- Sink: MySQL `funnel_results` rows: `(funnelId, projectId, stepIndex, userCount, windowStart, windowEnd)`.

## Tests

JUnit tests in `backend/spark/src/test/java/...` — focus on `FilterFieldMapper` and `FunnelFilterOperators`; full-job smoke tested manually against a staging ClickHouse.

## History / decisions

Push-down to ClickHouse for step filtering (instead of pulling raw events into Spark) because trace volumes are huge and filter selectivity is high.

## Rebuild recipe

1. Define `FunnelDefinition` model + MySQL table.
2. Implement `FilterFieldMapper` to handle every UI filter field.
3. Loop steps; push down CH queries; build step DataFrames.
4. Join in temporal order; count distinct users per step.
5. Write `FunnelResult` rows.
