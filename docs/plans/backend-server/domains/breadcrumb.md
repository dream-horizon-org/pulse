# domains / breadcrumb

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [session](session.md)

## Purpose

Session breadcrumb retrieval — chronological event trail for a session.

## Source

- `resources/breadcrumb/v1/GetSessionBreadcrumbs.java`
  (`@Path("/v1/breadcrumbs")`, `@POST`)
- `resources/breadcrumb/models/`
- `service/breadcrumb/BreadcrumbService.java`,
  `BreadcrumbServiceImpl.java`

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/breadcrumbs` |

Body: filter DTO (sessionId, time range, types) — see
`resources/breadcrumb/models/`.

## Internal design

Service queries ClickHouse `otel_logs`/`otel_traces` filtered by
`SessionId` + `PulseType` (`app.click`, `screen_load`, `http`, etc.) and
returns ordered events. No MySQL state.

## Dependencies

ClickHouse via `IAnalyticalStoreClient`; [session](session.md) for
session-id resolution.

## Data contracts

ClickHouse columns: `Timestamp`, `SessionId`, `PulseType`, `Platform`,
`SpanAttributes`/`LogAttributes` map.

## Tests

`src/test/java/.../resources/breadcrumb/*`,
`.../service/breadcrumb/*`.

## Rebuild recipe

1. Single POST resource accepting filter body.
2. Service composes ClickHouse SQL with `ProjectId`, `Timestamp` range,
   `LIMIT`.
3. Map rows to breadcrumb DTOs ordered by `Timestamp ASC`.
