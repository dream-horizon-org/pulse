# domains / analytics

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [productAnalysis](productAnalysis.md), [query](query.md)

## Purpose

Internal batch endpoints to (re)compute analytics (funnels, journeys) via
ClickHouse / EMR Serverless / Spark.

## Source

- `resources/analytics/InternalAnalyticsController.java`
  (`@Path("/internal/analytics")`)
- `resources/analytics/package-info.java`
- `service/analytics/AnalyticsBatchService.java`,
  `AnalyticsBatchServiceImpl.java`
- `service/analytics/ClickHouseBatchServiceImpl.java`,
  `ClickHouseComputeService.java`
- `service/analytics/ClickHouseFunnelComputeDao.java`,
  `ClickHouseJourneyComputeDao.java`
- `service/analytics/ClickhouseAnalyticsQueryUtils.java`,
  `ClickhouseAnalyticsConstantsMapper.java`
- `service/analytics/RoutingAnalyticsBatchService.java`

## Public surface

| Method | Path |
|---|---|
| POST | `/internal/analytics/funnels` |
| POST | `/internal/analytics/journeys` |
| POST | `/internal/analytics/events` |

All are service-to-service (`/internal/*`) — auth'd via API key.

## Internal design

- Controller delegates to `AnalyticsBatchService` (impl
  `AnalyticsBatchServiceImpl`).
- `RoutingAnalyticsBatchService` chooses backend (ClickHouse vs EMR/Spark)
  based on input scale.
- `ClickHouse*ComputeDao` builds funnel/journey aggregations directly on
  ClickHouse.
- Result-store is owned by the [productAnalysis](productAnalysis.md) domain
  (`funnelresults`, `journeyresults` DAOs).

## Dependencies

ClickHouse (read), EMR Serverless / Spark (compute), MySQL
(`analyticsjob` DAO for job state).

## Data contracts

- ClickHouse `otel_traces`/`otel_logs` filtered by `ProjectId`,
  `Timestamp`, `PulseType`.
- MySQL `dao/analyticsjob/` for run state.
- Output written to `funnelresults` / `journeyresults` tables.

## Tests

`src/test/java/.../service/analytics/*`,
`src/test/java/.../service/funnel/*`, `.../service/journey/*`.

## Rebuild recipe

1. `InternalAnalyticsController` with three POSTs.
2. `AnalyticsBatchService` interface; impls per backend
   (ClickHouse vs Spark).
3. `RoutingAnalyticsBatchService` selects impl.
4. Funnel/journey ClickHouse SQL in dedicated DAOs.
