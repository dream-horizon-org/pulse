# domains / query

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [clickhouse-access](../core/clickhouse-access.md)

## Purpose

Ad-hoc query engine over the analytics store (ClickHouse / Athena): submit,
job status, history, statistics, cancel, schema.

## Source

- `resources/query/v1/SubmitQuery.java` (POST `/query`)
- `resources/query/v1/CancelQuery.java` (DELETE `/query/job/{jobId}`)
- `resources/query/v1/GetQueryJobStatus.java` (GET `/query/job/{jobId}`)
- `resources/query/v1/GetQueryHistory.java` (GET `/query/history`)
- `resources/query/v1/GetQueryStatistics.java` (GET `/query/stats`)
- `resources/query/v1/GetTablesAndColumns.java` (GET `/query/tables`)
- `resources/query/models/`
- `service/query/QueryService.java`, `QueryServiceImpl.java`,
  `QueryStatisticsService.java`, `models/`
- `dao/query/QueryJobDao.java`, `AlertsQuery.java`,
  `UserExperienceCategoriesQuery.java`
- `module/QueryEngineModule.java`

## Public surface

| Method | Path |
|---|---|
| POST | `/query` |
| DELETE | `/query/job/{jobId}` |
| GET | `/query/job/{jobId}` |
| GET | `/query/history` |
| GET | `/query/stats` |
| GET | `/query/tables` |

## Internal design

- Each path lives in its own resource class (`v1/*.java`) — keeps Jakarta
  routing flat.
- `QueryService` orchestrates: persist job (`QueryJobDao`), submit to
  backend (ClickHouse or Athena via `IAnalyticalStoreClient`), poll.
- `QueryStatisticsService` aggregates usage metrics for `/stats`.
- Athena path lives under `service/athena/` and `dao/athena/`.
- `QueryEngineModule` binds services and clients.

## Dependencies

ClickHouse, Athena, MySQL (`query_jobs`), [auth](../core/auth.md).

## Data contracts

MySQL `query_jobs(id, project_id, user_id, sql, backend, status,
result_uri, started_at, finished_at)`.

## Tests

`src/test/java/.../resources/query/*`, `.../resources/v1/*Query*`,
`.../service/query/*`, `.../service/athena/*`.

## Rebuild recipe

1. One resource class per endpoint (mirrors v1 folder).
2. `QueryService` with submit/cancel/status/history.
3. `QueryJobDao` for persistence.
4. Backend selection via `IAnalyticalStoreClient`.
5. Install `QueryEngineModule` in `MainModule`.
