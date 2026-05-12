# spark-jobs — Plan Handbook

Batch analytics jobs (Java, Apache Spark) that aggregate ClickHouse/MySQL data into derived tables used by the UI funnel/journey/event-catalog screens.

Brief: [../../components/spark-jobs.md](../../components/spark-jobs.md)

> Note on stack: the code is **Java** (not Scala) under `backend/spark/src/main/java/org/dreamhorizon/pulsespark/`. Maven-built.

## Jobs (sub-files)

| File | Covers |
|---|---|
| [funnel-compute.md](./jobs/funnel-compute.md) | `FunnelComputeJob.java` — funnel definitions → step conversions + results |
| [journey-compute.md](./jobs/journey-compute.md) | `JourneyComputeJob.java` — user journey transitions |
| [event-catalog.md](./jobs/event-catalog.md) | `EventCatalogJob.java` — enumerates all event types seen per project |

## Shared infrastructure

- `SparkJobRunner.java` — main class; picks a job by CLI arg.
- `MysqlRepository.java` — reads funnel/journey definitions from MySQL, writes results.
- `ClickHouseClient.java` — ClickHouse HTTP client for source reads.
- `AwsSecretsHelper.java` — Secrets Manager lookup for DB creds.
- `FilterFieldMapper.java`, `FunnelFilterOperators.java` — translate UI filters → SQL.
- Models under `org.dreamhorizon.pulsespark.model`: `FunnelDefinition`, `FunnelStep`, `FunnelFilter`, `FunnelResult`, `JourneyDefinition`, `JourneyTransition`.

## Reading order

1. Brief.
2. `SparkJobRunner` in source (entry point).
3. Job file for the job you're working on.
4. `MysqlRepository` / `ClickHouseClient` if the question is about I/O.

## Rebuild checklist

1. `pom.xml` with Spark 3.x + ClickHouse JDBC + MySQL connector deps.
2. `SparkJobRunner` main class dispatching on arg.
3. One job class per workload (see sub-files).
4. Schedule on a Spark-on-K8s or EMR cluster; inputs: `--project-id`, `--start`, `--end`.
5. Results write back to MySQL tables consumed by `backend/server` (see `resources/productAnalysis/`, `resources/eventdefinition/`).
