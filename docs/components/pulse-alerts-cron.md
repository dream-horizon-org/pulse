# pulse-alerts-cron

## What

Scheduled alert evaluation service. Periodically fetches alert configs from
`pulse-server` over REST, groups them by evaluation interval, and triggers
each alert's evaluation endpoint on a Vert.x periodic timer. The actual
ClickHouse query, threshold check and notification dispatch live in
`pulse-server`; this service is the time-keeper / fan-out that keeps
evaluation cadence honest under load.

It also runs periodic sync jobs (usage credits, API keys, usage-limit
emails) that POST to `pulse-server` async endpoints.

## Path + stack

- Path: `backend/pulse-alerts-cron/`
- Language / runtime: Java 17
- Framework: Vert.x 4.5 (RxJava3 bindings) + Guice + Lombok
- HTTP scheduling: `io.vertx.rxjava3.core.Vertx#setPeriodic` (no Quartz)
- Config: Typesafe HOCON (`src/main/resources/conf/application-default.conf`)
- Build: Maven (`pom.xml`, artifact `pulse-alerts-cron`, version
  `1.5-SNAPSHOT`)
- Default REST port: 4000 (per repo CLAUDE.md; bound via `RestVerticle`)
- Logging: Logback (`src/main/resources/logback/logback.xml`)

## Build

```bash
cd backend/pulse-alerts-cron
mvn clean install              # compile + checkstyle + tests + JaCoCo
mvn verify
mvn -Dtest=CronTaskTest test    # single test class
```

Container image: `Dockerfile` + `docker-entrypoint.sh`.

## Inputs + outputs

Inputs:

- HTTP REST (admin/control): `CronController` exposes add/update/delete
  cron endpoints; `HealthCheckController` for liveness.
- Pulse server `GET /alerts` → list of `Alert` configs (id, interval,
  projectId).
- Config (env-substituted): `pulseServerUrl`, ClickHouse host/port (used
  by `pulse-server`, not directly here), Redis host/port,
  `batchScheduleTime`, `batchJobsEnabled`, sync intervals.

Outputs:

- HTTP POST `pulseServerUrl + /v1/alert/evaluateAndTriggerAlert?alertId={id}`
  per scheduled tick (see `AlertsService.ALERT_EVALUATION_PATH`).
- HTTP POST to batch endpoints (`batchFunnelsEndpoint`,
  `batchJourneysEndpoint`, `batchEventsEndpoint`) on the configured
  `batchScheduleTime`.
- HTTP POST to usage-credits / api-keys / usage-limit-notification
  endpoints on their own intervals.

Pulse-server is responsible for: building the ClickHouse query,
evaluating thresholds, and fanning out to email / Slack / webhook
channels. This service is intentionally thin.

## Key files

- `verticle/MainVerticle.java` — boot, deploy `RestVerticle`, start
  `CronManager` + `PeriodicSyncService` + `BatchSchedulerService`.
- `verticle/RestVerticle.java` — HTTP listener (port 4000).
- `services/CronManager.java` — interval → `CopyOnWriteArrayList<CronTask>`
  groups; one `vertx.setPeriodic(interval*1000, ...)` per interval;
  retry with exponential backoff (`MAX_RETRY_ATTEMPTS=3`,
  `INITIAL_RETRY_DELAY_MS=1000`, `REQUEST_TIMEOUT_MS=30000`).
- `services/AlertsService.java` — REST call to pulse-server `/alerts`,
  decorates each `Alert` with evaluation URL.
- `services/BatchSchedulerService.java` — runs Spark-trigger batch
  endpoints on cron expression.
- `services/PeriodicSyncService.java` — usage credits, API keys, usage
  limit notifications.
- `services/DataSyncService.java` — pull-and-cache from pulse-server.
- `client/PulseServerApiClient.java` — Vert.x `WebClient` wrapper.
- `module/ConfigModule.java`, `module/VertxAbstractModule.java` — Guice
  bindings.
- `error/ServiceError.java` — typed error codes.
- `dao/HealthCheckDao.java` — DB liveness probe.
- `models/{Alert,CronTask,Group,Interval}.java` — Lombok DTOs.

Conventions mirror `backend/server/`: `@RequiredArgsConstructor(onConstructor
= @__({@Inject}))`, Google checkstyle (140-char lines, 2-space indent),
JaCoCo 35% overall / 80% on changed files, JUnit 5 + Mockito + AssertJ
with `should*` method names.

## Owners

- Owner: _TBD_
- Backup: _TBD_

## Plan

See [`docs/plans/pulse-alerts-cron/index.md`](../plans/pulse-alerts-cron/index.md).
