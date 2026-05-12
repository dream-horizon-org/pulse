# core / clickhouse-access

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Per-project ClickHouse connection pools for analytics reads (and limited
writes).

## Source

- `.../client/chclient/ClickhouseProjectConnectionPoolManager.java`
- `.../client/chclient/ClickhouseReadClient.java`
- `.../client/chclient/ClickhouseWriteClient.java`
- `.../client/chclient/ClickhouseWriteClientProvider.java`
- `.../client/chclient/ClickhouseQueryService.java`
- `.../service/ClickhouseProjectService.java`
- `.../service/IAnalyticalStoreClient.java` (analytical store abstraction)
- `.../dao/clickhouseprojectcredentials/ClickhouseProjectCredentialsDao.java`
- Config: `src/main/resources/conf/clickhouse-default.conf`

## Public surface

`ClickhouseProjectService` resolves per-project credentials and returns a
read/write client. `IAnalyticalStoreClient` is the higher-level facade so
services can swap stores (Athena vs ClickHouse).

## Internal design

- Credentials per `projectId` in MySQL
  (`clickhouseprojectcredentials` table) — never use admin in app code.
- `ClickhouseProjectConnectionPoolManager` caches pools by `projectId`.
- Read path: SQL strings from per-domain `Queries.java` (e.g.
  `dao/heatmap/HeatmapQueries.java`, `dao/sessiondetail/SessionDetailQueries.java`).
- Materialized columns preferred over map access (see CLAUDE.md):
  `ProjectId`, `PulseType`, `Platform`, `AppVersion`, `SessionId`.
- All queries must include time-range on `Timestamp`, `LIMIT`, and
  `ProjectId` filter (multi-tenant row policies).

## Gotchas

- Multi-tenant isolation depends on filtering — never trust caller IDs.
- Pool exhaustion if `projectId` cardinality explodes; pools are evicted by
  manager.
- Write client used sparingly (e.g. ingestion bookkeeping); main writes flow
  via OTEL Collector.

## Dependencies

ClickHouse JDBC / HTTP, Vert.x WebClient, MySQL for credentials.

## Data contracts

Database: `otel`. Tables: `otel_traces`, `otel_logs`,
`otel_metrics_gauge`, `stack_trace_events`, `interaction_heatmaps_daily` (see
CLAUDE.md table).

## Tests

`src/test/java/.../service/ClickhouseProjectServiceTest.java`,
`.../service/clickhouse/*`.

## Rebuild recipe

1. Add credential DAO returning `(host, port, user, password, db)` per project.
2. `ClickhouseProjectConnectionPoolManager` with `Caffeine`-style cache.
3. `ClickhouseReadClient` + `ClickhouseWriteClient` wrapping JDBC/HTTP.
4. `IAnalyticalStoreClient` facade routing Athena vs ClickHouse.
5. Enforce per-query: project filter, `Timestamp` range, `LIMIT`.
