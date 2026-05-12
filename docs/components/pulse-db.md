# pulse-db

Pulse's persistence layer. Two engines with disjoint responsibilities:

- **MySQL** (`backend/db/{dev,prod}/mysql/mysql-init.sql`) — control-plane / OLTP state: tenants, projects, users, API keys, OAuth/JWT material, alert configs, critical-interaction configs, event definitions, tiers, usage limits, sampling configs, personal tokens, notification routing, RCA caches.
- **ClickHouse** (`backend/db/{dev,prod}/clickhouse/*.sql`) — telemetry / OLAP store under database `otel`: OTel traces / logs / metrics, derived analytics tables, and Pulse-specific tables (stack traces, replay events, heatmaps, funnel/journey results, session summary).

## Source layout

```
backend/db/
├── dev/
│   ├── mysql/mysql-init.sql                 (~94 KB; full bootstrap incl. seed data)
│   └── clickhouse/                          (16 numbered .sql files, applied in order)
│       ├── 01_otel.otel_logs.sql
│       ├── 02_otel.otel_traces.sql
│       ├── 03_otel.stack_trace_events.sql
│       ├── 04_otel.funnel_results.sql
│       ├── 05_otel.journey_results.sql
│       ├── 06_otel.root_cause_cache.sql
│       ├── 07_otel.screen_root_cause_cache.sql
│       ├── 08_otel.project_monthly_usage.sql          (+ 3 MVs)
│       ├── 09_otel.session_replay_events.sql          (+ MV)
│       ├── 10_otel.otel_metrics_sum.sql               (+ MV)
│       ├── 11_otel.otel_metrics_histogram.sql         (+ MV)
│       ├── 12_otel.otel_metrics_exp_histogram.sql     (+ MV)
│       ├── 13_otel.otel_metrics_summary.sql           (+ MV)
│       ├── 14_otel.interaction_heatmaps_daily.sql     (+ MV)
│       ├── 15_otel.event_catalog_entries.sql
│       └── 16_otel.session_summary.sql
└── prod/
    ├── mysql/mysql-init.sql
    └── clickhouse/                          (same tables, file-per-table naming)
```

`dev/` and `prod/` MySQL scripts differ (seed data, defaults). `dev/clickhouse/` files use numeric prefixes so `init-clickhouse.sh` applies them in deterministic order; `prod/` drops the prefix.

## MySQL (database `pulse_db`, plus `openfga`)

Charset `utf8mb4` / collation `utf8mb4_unicode_ci`. Multi-tenant via `tenant_id` FKs.

Tables (from `CREATE TABLE` in `mysql-init.sql`):

```
tiers, tenants, users, projects,
interaction, suggested_interaction, symbol_files, pulse_sdk_configs,
severity, notification_channels_old,
alerts, alert_scope, alert_evaluation_history, scope_types, alert_metrics,
athena_job, project_usage_limits, project_api_keys,
clickhouse_project_credentials, clickhouse_project_credential_audit,
tnc_versions, tnc_acceptances,
notification_channels, notification_templates, channel_event_mapping,
notification_logs, email_suppression_list,
rca_report_cache, rca_report_jobs,
event_definitions, event_attribute_definitions,
incidents, usage_limit_notifications, cron_jobs_history,
funnel, journey, funnel_journey_tag, analytics_jobs
```

`openfga` database is created empty; OpenFGA migrates its own schema at boot. Dev seed creates tenants `default`, `Fancode`, `Dream11` and seeds mock users (`mock-user-1`, `mock-user-2`) plus the default-project SDK config and sample interactions.

## ClickHouse (`otel` database)

Engines: all base tables are `MergeTree` partitioned by `toYYYYMMDD(Timestamp)` and ordered by `(ProjectId, PulseType, …, Timestamp)`. Metric tables follow OTel ClickHouse exporter contract.

Materialized columns extracted from Map attributes (the rule "always use materialized columns over map access"):

| Column            | Source                                                  |
|-------------------|---------------------------------------------------------|
| `ProjectId`       | `ResourceAttributes['project.id']`                      |
| `PulseType`       | `SpanAttributes / LogAttributes['pulse.type']`          |
| `Platform`        | `ResourceAttributes['os.name']` (logs); `os.type` (traces) |
| `AppVersion`      | `ResourceAttributes['app.build_name' / 'app.version']`  |
| `SessionId`       | `SpanAttributes / LogAttributes['session.id']`          |
| `UserId`          | `…['user.id']`                                          |
| `AppInstallationId` | `…['app.installation.id']`                            |
| `EventName`       | `Body` when `pulse.type = 'custom_event'`               |
| `HttpUrl`, `HttpMethod`, `HttpStatusCode` | http.* / url.* fallbacks       |
| `ScreenName`, `ClickType`, `Rage`, `XPer`/`YPer` | interaction attrs        |

Bloom-filter indexes on `TraceId`, `SessionId`, `UserId`, `AppInstallationId`, `SpanId`, `ScreenName`.

### Row policies

Per-project isolation is enforced by **row policies created at runtime** by `backend/server` (`ClickhouseProjectService.java`) — not in the init SQL. Each project gets a dedicated CH user + row policies filtering by `ProjectId`. Application code must connect using the tenant credentials, never the admin user.

### Query rules

Every CH query the app issues must include:
1. time-range on `Timestamp`,
2. `ProjectId` filter,
3. `LIMIT`.

## Migrations

There is no incremental migration tool. Both dev and prod are bootstrap-only:
- MySQL: `docker-compose` mounts `mysql-init.sql` into `/docker-entrypoint-initdb.d/`; runs once on fresh data dir.
- ClickHouse: `deploy/scripts/init-clickhouse.sh` waits for CH ready, then applies every `.sql` in lex order.

Schema evolution happens by editing the init SQL and either (a) running `deploy/scripts/reset-databases.sh` (destructive — drops volumes), or (b) applying the diff manually against a running cluster.

## Plans

See `/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/pulse-db/index.md` for the per-table / per-subsystem deep dives.

## Cross-links

- Consumers: `backend/server/` DAOs, `backend/pulse-alerts-cron/`.
- Writers into ClickHouse: `backend/ingestion/otel-collector.yaml` (traces, logs, metrics), `vector/vector.yaml` (`project_monthly_usage`).
- Bootstrap: `deploy/docker-compose.yml` services `mysql`, `clickhouse`, `clickhouse-init`.
