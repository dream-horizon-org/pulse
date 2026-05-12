# ClickHouse — materialized columns

## Purpose

OTel data lands with attributes in `Map(LowCardinality(String), String)` columns (`ResourceAttributes`, `SpanAttributes`, `LogAttributes`, `ScopeAttributes`). Map access is slow and prevents primary-key / skip-index use. Pulse extracts hot attributes into materialized scalar columns so queries can filter and aggregate on them directly.

**Rule:** application code must use the materialized column, never `Attributes['x']`.

## Source

- `backend/db/dev/clickhouse/01_otel.otel_logs.sql`
- `backend/db/dev/clickhouse/02_otel.otel_traces.sql`
- Equivalent prod files.

## Logs (`otel_logs`)

| Column            | Type                   | Source (`ifNull(…, '')` unless noted)              |
|-------------------|------------------------|-----------------------------------------------------|
| `SessionId`         | String                 | `LogAttributes['session.id']`                     |
| `MeteringSessionId` | String                 | `LogAttributes['pulse.metering.session.id']`      |
| `ProjectId`         | LowCardinality(String) | `ResourceAttributes['project.id']`                |
| `AppVersion`        | LowCardinality(String) | `ResourceAttributes['app.build_name']`            |
| `SDKVersion`        | LowCardinality(String) | `ResourceAttributes['rum.sdk.version']`           |
| `Platform`          | LowCardinality(String) | `ResourceAttributes['os.name']`                   |
| `OsVersion`         | LowCardinality(String) | `ResourceAttributes['os.version']`                |
| `GeoState`          | LowCardinality(String) | `LogAttributes['geo.region.iso_code']`            |
| `GeoCountry`        | LowCardinality(String) | `LogAttributes['geo.country.iso_code']`           |
| `DeviceModel`       | LowCardinality(String) | `ResourceAttributes['device.model.name']`         |
| `NetworkProvider`   | LowCardinality(String) | `LogAttributes['network.carrier.name']`           |
| `UserId`            | String                 | `LogAttributes['user.id']`                        |
| `AppInstallationId` | String                 | `LogAttributes['app.installation.id']`            |
| `PulseType`         | LowCardinality(String) | `LogAttributes['pulse.type']` default `'otel'`    |
| `EventName`         | LowCardinality(String) | `Body` when `PulseType = 'custom_event'`          |
| `ScreenName`        | LowCardinality(String) | `LogAttributes['screen.name']`                    |
| `ClickType`         | LowCardinality(String) | `LogAttributes['click.type']`                     |
| `Rage`              | Bool                   | `LogAttributes['click.is_rage'] = 'true'`         |
| `RageCount`         | UInt8                  | `toUInt8OrZero(LogAttributes['click.rage_count'])` |
| `XPer` / `YPer`     | Float32                | `app.screen.coordinate.x` / `.y`                  |
| `NormXPer` / `NormYPer` | Float32            | `app.screen.coordinate.nx` / `.ny`                |
| `ViewportWidth/Height` | UInt16              | `device.screen.width` / `.height`                 |
| `AspectRatio`       | LowCardinality(String) | `device.screen.aspect_ratio`                      |

Skip indexes: bloom_filter on `TraceId`, `SessionId`, `UserId`, `AppInstallationId`, `SpanId`, `ScreenName`; set(32) on `SeverityNumber`.

## Traces (`otel_traces`)

| Column            | Source                                                                  |
|-------------------|-------------------------------------------------------------------------|
| `ProjectId`         | `ResourceAttributes['project.id']`                                    |
| `SpanType`          | `SpanAttributes['pulse.type']`                                        |
| `PulseType`         | `SpanAttributes['pulse.type']`                                        |
| `SessionId`         | `SpanAttributes['session.id']`                                        |
| `AppVersion`        | `ResourceAttributes['app.version']`                                   |
| `SDKVersion`        | `ResourceAttributes['telemetry.sdk.version']`                         |
| `Platform`          | `ResourceAttributes['os.type']`                                       |
| `OsVersion`         | `ResourceAttributes['os.version']`                                    |
| `GeoState/Country`  | `SpanAttributes['geo.region.iso_code' / 'geo.country.iso_code']`      |
| `DeviceModel`       | `ResourceAttributes['device.model.identifier']`                       |
| `NetworkProvider`   | `SpanAttributes['network.carrier.name']`                              |
| `MeteringSessionId` | `SpanAttributes['metering.session.id']`                               |
| `UserId`            | `SpanAttributes['user.id']`                                           |
| `AppInstallationId` | `SpanAttributes['app.installation.id']`                               |
| `HttpUrl`           | coalesce `SpanAttributes['http.url']`, `['url.full']`                 |
| `HttpHost`          | `SpanAttributes['net.peer.name']` else `['server.address']`            |
| `HttpMethod`        | coalesce `['http.method']`, `['http.request.method']`                 |
| `HttpStatusCode`    | UInt16; coalesce `['http.status_code']`, `['http.response.status_code']` |
| `GraphqlType/Name`  | `SpanAttributes['graphql.operation.type'/'name']`                     |
| `ScreenName`        | `SpanAttributes['screen.name']`                                       |

## Asymmetries to know

- Logs use `os.name` for `Platform`; traces use `os.type`. SDKs must populate the right resource key per signal.
- Logs use `app.build_name` for `AppVersion`; traces use `app.version`.
- Logs use `device.model.name`; traces use `device.model.identifier`.
- `EventName` is only meaningful when `PulseType = 'custom_event'` — otherwise empty.

## Query rules

1. Always filter on `ProjectId = <const>`.
2. Always include a `Timestamp` range (`Timestamp >= … AND Timestamp < …`).
3. Always `LIMIT`.
4. Prefer the column over the map. The Java DAOs in `backend/server/.../dao/` enforce this by construction (SQL in `Queries.java`).

## Adding a new materialized column

1. Edit the table SQL in both `dev/` and `prod/`.
2. `ALTER TABLE otel.<tbl> ADD COLUMN <name> <type> MATERIALIZED <expr>` for live clusters (does not backfill — existing rows have default).
3. For backfill, either `OPTIMIZE … FINAL` (small datasets) or `ALTER … MATERIALIZE COLUMN <name>`.
4. Update DAOs in `backend/server/`.
