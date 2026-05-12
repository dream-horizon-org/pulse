# spark-jobs

## What

Batch analytics jobs that read events from ClickHouse, join with config
data in MySQL (funnel/journey definitions, event catalog metadata), and
write rollups back to MySQL (and intermediate state to S3 when needed).
A single entrypoint, `SparkJobRunner`, dispatches on `--job_type`:

- `FUNNELS_DAILY` / `FUNNEL` — funnel computation
- `JOURNEYS_DAILY` / `JOURNEY` — journey / transition computation
- `EVENTS_INCREMENTAL` — event catalog incremental build

Note: the source is **Java** (Spark Java API), not Scala — the
Scala-binary dependency in `pom.xml` is the Spark artifact suffix
(`scala.binary.version=2.12`). Source lives under
`backend/spark/src/main/java/org/dreamhorizon/pulsespark/`.

## Path + stack

- Path: `backend/spark/`
- Language: Java 17
- Engine: Apache Spark 3.5.3 (`spark-core_2.12`, `spark-sql_2.12`)
- Connectors: `mysql-connector-j`, native ClickHouse HTTP via
  `ClickHouseClient.java`, AWS Secrets Manager
  (`aws-java-sdk-secretsmanager`), Jackson Databind.
- Logging: Log4j2.
- Build: Maven shade plugin → fat jar.
- Triggering: `pulse-alerts-cron`'s `BatchSchedulerService` POSTs to the
  pulse-server batch endpoints which submit Spark jobs.

## Build

```bash
cd backend/spark
mvn clean package               # produces shaded jar under target/
```

Submit (example):

```bash
spark-submit --class org.dreamhorizon.pulsespark.SparkJobRunner \
  target/pulse-spark-jobs-*.jar \
  --job_type FUNNELS_DAILY \
  --spark_job_id 123 \
  --clickhouse_host ... --clickhouse_port 8123 \
  --mysql_host ... --mysql_port 3306 \
  --s3_bucket_prefix pulse-otel- --aws_region ap-south-1
```

## Inputs + outputs

Inputs:

- CLI args parsed by `SparkJobRunner`: `--job_type`, `--reference_id`,
  `--spark_job_id` (MySQL `analytics_jobs.id`), `--secrets_name`,
  `--aws_region` (default `ap-south-1`), `--s3_bucket_prefix`
  (default `pulse-otel-`), ClickHouse + MySQL connection params.
- Secrets via AWS Secrets Manager (`AwsSecretsHelper.java`) when
  `--secrets_name` provided.
- ClickHouse `otel.*` tables — read via `ClickHouseClient` (HTTP).
- MySQL — funnel/journey definitions and filter mappings via
  `MysqlRepository.java`.

Outputs:

- MySQL — funnel results, journey transitions, event catalog rows
  (writes via `MysqlRepository`); job-row status update on
  `analytics_jobs.id = --spark_job_id`.
- S3 (intermediate / staged) — bucket prefix from `--s3_bucket_prefix`.
- Logs via Log4j2 to stdout (driver) for Spark UI capture.

The brief mentions "heatmap aggregation" — there is no heatmap Spark
job in source today; the closest siblings are the funnel/journey/event
jobs documented above. Heatmap rollups currently live in ClickHouse
(`interaction_heatmaps_daily`) and the ingestion service.

## Key files

- `SparkJobRunner.java` — single entrypoint, arg parsing, dispatcher,
  `DURATION_PATTERN = ^(\d+)(ms|s|m|h)$`.
- `FunnelComputeJob.java` — funnel step / conversion computation using
  Spark SQL `Window` + aggregations; consumes `FunnelDefinition`,
  emits `FunnelResult`.
- `JourneyComputeJob.java` — journey transition extraction with
  windowed lag/lead.
- `EventCatalogJob.java` — incremental event catalog build; filter
  keys: `EVENT`, `APP_BUILD_NAME`, `OS_VERSION`, `OS_NAME`.
- `FunnelFilterOperators.java` — filter operator semantics.
- `FilterFieldMapper.java` — maps DSL field → ClickHouse column.
- `ClickHouseClient.java` — HTTP query client (used to push down
  filters / read raw events).
- `MysqlRepository.java` — JDBC reads/writes for funnels, journeys,
  job-status rows.
- `AwsSecretsHelper.java` — Secrets Manager lookup.
- `model/{FunnelDefinition,FunnelFilter,FunnelStep,FunnelResult,
  JourneyDefinition,JourneyTransition}.java` — POJOs.

## Owners

- Owner: _TBD_
- Backup: _TBD_

## Plan

See [`docs/plans/spark-jobs/index.md`](../plans/spark-jobs/index.md).
