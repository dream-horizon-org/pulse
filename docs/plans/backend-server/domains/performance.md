# domains / performance

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [interaction](interaction.md)

## Purpose

Latency / performance metric distribution for interactions.

## Source

- `resources/performance/PerformanceMetricDistribution.java`
  (`@Path("/v1/interactions/performance-metric/")`)
- `resources/performance/models/`
- `service/interaction/PerformanceMetricService.java` (shared with
  interaction domain)
- `service/interaction/ClickhouseMetricService.java`

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/interactions/performance-metric/distribution` |

Body: interaction name, time range, optional filters (platform, app version).

## Internal design

Service builds a ClickHouse aggregation (percentile buckets) over
`otel_traces` for the named interaction; returns histogram-style buckets.

## Dependencies

ClickHouse; shares `PerformanceMetricService` with
[interaction](interaction.md).

## Data contracts

ClickHouse `otel_traces` filtered by `ProjectId`, `PulseType=app.click`
(or `screen_load`), interaction attribute, `Timestamp` range.

## Tests

`src/test/java/.../resources/performance/*`.

## Rebuild recipe

1. Single POST resource accepting filter DTO.
2. Delegate to `PerformanceMetricService.distribution(...)`.
3. Return list of `(bucket, count)` plus p50/p90/p99 summary.
