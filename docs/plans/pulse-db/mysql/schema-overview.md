# MySQL — schema overview

## Purpose

The OLTP control plane for Pulse: tenants, projects, members, API keys, alerts, event definitions, interactions, SDK configs, RCA caches, notification routing, usage metering. Anything that is **read-write-mostly small** lives here; anything append-only-large lives in ClickHouse.

## Source

- `backend/db/dev/mysql/mysql-init.sql` (~94 KB, includes seed data).
- `backend/db/prod/mysql/mysql-init.sql` (~94 KB, no dev seed).
- `deploy/db/mysql-init.sql` — copy mounted into the `mysql` container in dev.

Charset `utf8mb4`, collation `utf8mb4_unicode_ci`. Database name `pulse_db`. A sibling database `openfga` is created empty and populated by OpenFGA's own migrator (`openfga-migrate` service).

## Inventory

```
tiers
tenants
users
projects
interaction
suggested_interaction
symbol_files
pulse_sdk_configs
severity
notification_channels_old
alerts
alert_scope
alert_evaluation_history
scope_types
alert_metrics
athena_job
project_usage_limits
project_api_keys
clickhouse_project_credentials
clickhouse_project_credential_audit
tnc_versions
tnc_acceptances
notification_channels
notification_templates
channel_event_mapping
notification_logs
email_suppression_list
rca_report_cache
rca_report_jobs
event_definitions
event_attribute_definitions
incidents
usage_limit_notifications
cron_jobs_history
funnel
journey
funnel_journey_tag
analytics_jobs
```

## Foreign-key spine

```
tenants ──< projects ──< project_api_keys
                     ──< project_usage_limits ──> tiers
                     ──< pulse_sdk_configs
                     ──< clickhouse_project_credentials ──< clickhouse_project_credential_audit
                     ──< interaction ──< suggested_interaction
                     ──< symbol_files
                     ──< alerts ──< alert_scope ──> scope_types
                                ──< alert_evaluation_history
                                ──< alert_metrics
                     ──< event_definitions ──< event_attribute_definitions
                     ──< funnel / journey ──< funnel_journey_tag
                     ──< athena_job
                     ──< rca_report_cache / rca_report_jobs
                     ──< incidents
users (auth) — referenced by alerts.created_by, projects.owner, etc.
```

## Dev seed

`dev/mysql/mysql-init.sql` inserts:
- Tenants: `default`, `Fancode`, `Dream11`.
- Default tenant projects (incl. `default-project`).
- Mock users `mock-user-1`, `mock-user-2` (used when `GOOGLE_OAUTH_ENABLED=false`).
- Default-project interactions (1..5 web flows matching `pulse-web-otel/examples/ecommerce-demo/public/interaction-config.mock.json`; legacy `BasicInteraction` + `FullShopping`).
- `pulse_sdk_configs` template documented inline (sampling, signals, interaction, features blocks).
- Default API key `default-project_devkey01` (when `DEV_MODE_API_KEY` is set).

Prod init drops the seed; tenants/projects are created via API.

## Operational notes

- Docker Compose mounts `mysql-init.sql` into `/docker-entrypoint-initdb.d/`. MySQL only runs it on a fresh data dir — so editing the file does nothing unless you wipe `mysql-data` volume.
- Pool sizing: `MYSQL_WRITER_MAX_POOL_SIZE`, `MYSQL_READER_MAX_POOL_SIZE` (default 10 each).
- Host port `3307` (avoid clashing with a system MySQL on 3306).

## Failure modes

- "Table `pulse_db.users` doesn't exist" → MySQL booted before init SQL applied; fix with `deploy/scripts/create-users-table.sh`.
- Default tenant CH password drift → `deploy/scripts/sync-default-tenant-ch-credentials.py`.

## Related code

- DAOs in `backend/server/src/main/java/org/dreamhorizon/pulseserver/dao/`.
- Alerts cron reads `alerts`, `alert_scope`, `alert_evaluation_history`.
- `ClickhouseProjectService` reads/writes `clickhouse_project_credentials` + audit.

## Open questions

- No incremental migration tool — every schema change requires bootstrap edit + (dev) volume reset or hand-applied SQL on running clusters.
