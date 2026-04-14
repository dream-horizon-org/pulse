---
paths:
  - "**/*.sql"
  - "backend/ingestion/**"
---

# ClickHouse SQL Conventions

## Database: `otel`

## Core Tables

| Table | Engine | Purpose |
|-------|--------|---------|
| `otel_traces` | MergeTree | Spans — TraceId, SpanId, SpanName, Duration, SpanAttributes |
| `otel_logs` | MergeTree | Logs — TraceId, Body, SeverityText, LogAttributes |
| `otel_metrics_gauge` | MergeTree | Gauge metrics |
| `stack_trace_events` | MergeTree | Symbolicated + grouped stack traces |
| `interaction_heatmaps_daily` | SummingMergeTree | Daily binned heatmap aggregates |
| `interaction_heatmaps_daily_mv` | Materialized View | Aggregates from `otel_logs` where `PulseType = 'app.click'` |

## Materialized Columns — Always Prefer Over Map Access

| Column | Source Key | Source Map |
|--------|-----------|------------|
| `ProjectId` | `project.id` | ResourceAttributes |
| `PulseType` | `pulse.type` | SpanAttributes/LogAttributes |
| `AppVersion` | `app.build_name` | ResourceAttributes |
| `Platform` | `os.name` | ResourceAttributes |
| `GeoState` | `geo.region.iso_code` | SpanAttributes/LogAttributes |
| `GeoCountry` | `geo.country.iso_code` | SpanAttributes/LogAttributes |
| `SessionId` | `session.id` | SpanAttributes/LogAttributes |
| `UserId` | `user.id` | SpanAttributes/LogAttributes |
| `DeviceModel` | `device.model.name` | ResourceAttributes |

## ORDER BY Keys (all start with `ProjectId`)

| Table | ORDER BY |
|-------|----------|
| `otel_traces` | `(ProjectId, ServiceName, PulseType, SpanName, Timestamp)` |
| `otel_logs` | `(ProjectId, ServiceName, PulseType, EventName, SeverityText, toUnixTimestamp(Timestamp), TraceId)` |
| `interaction_heatmaps_daily` | `(Date, ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin)` |

## Multi-Tenant Isolation

Each project has dedicated ClickHouse credentials + row policy filtering by `ProjectId`. Created via `ClickhouseProjectService` at project creation. Use tenant credentials for all queries — never the admin user in application code.

## Query Rules

- Always include time-range filter on `Timestamp`
- Always use `LIMIT`
- Use materialized columns over Map access (`Platform` not `ResourceAttributes['os.name']`)
- Prefer `toDateTime64()` for timestamp comparisons
- Partition by date for time-series tables

## Schema Conventions

- `MergeTree` with `ORDER BY` tuples for range queries
- `DateTime64(9)` for nanosecond timestamps
- `LowCardinality(String)` for materialized low-cardinality columns
- `Map(String, String)` for flexible attributes
