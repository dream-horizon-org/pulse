# domains / logs

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

OTLP log receiver at the REST tier (alongside the OTEL Collector path).

## Source

- `resources/logs/OtelLogsResource.java` (`@Path("/v1/logs")`, `@POST`)

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/logs` |

Body: OTLP logs JSON (per OpenTelemetry spec).

## Internal design

- Direct POST endpoint accepting OTLP-encoded logs.
- Used as fallback when the collector path is unavailable, or for limited
  client SDKs.
- Forwards into the analytics pipeline (collector / ClickHouse).

## Dependencies

ClickHouse `otel_logs` (eventual write); OTEL Collector.

## Data contracts

OTLP-Logs JSON. Required attributes: `project.id`, `pulse.type`,
`session.id`, `os.name`, `app.build_name`.

## Tests

Resource is thin; covered by `RestModelCoverageTest`.

## Rebuild recipe

1. Single Jakarta resource at `/v1/logs` accepting OTLP JSON.
2. Validate auth + project; forward to collector or write client.
