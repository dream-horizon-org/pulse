# pulse-ingestion — plan index

Companion brief: `/Users/ujjwal.bagrania/Desktop/pulse/docs/components/pulse-ingestion.md`.

The OTel Collector (`otel/opentelemetry-collector-contrib`) is the front door for traces/logs/metrics from the mobile and web SDKs. Athena DDL provisions per-project Parquet tables for custom events (written by Vector).

## Sub-plans

- [collector/otel-collector-config.md](collector/otel-collector-config.md) — full receivers / processors / exporters / extensions walk-through.
- [collector/pipelines.md](collector/pipelines.md) — pipeline wiring, the `routing/pulse` connector, traffic split between ClickHouse and `pulse-server`.
- [collector/athena-ddl.md](collector/athena-ddl.md) — `pulse_athena_db.otel_data_<project_id>` external-table contract.

## Source tree

```
backend/ingestion/
├── otel-collector.yaml         (113 lines)
└── athena-otel-tables.sql       (DDL template per project)
```

## Boundaries

- **In:** OTLP HTTP `:4318` (traces / metrics / logs). gRPC is **not** enabled here — gRPC OTLP (used for custom events) goes to Vector on `:4317/:4318`.
- **Out:** ClickHouse (traces / metrics / non-routed logs / metric tables), `pulse-server:8080` (crash/ANR/non-fatal logs).
- Athena does not run inside this container — DDL is applied externally per-project.

## Operational rules

1. `create_schema: false` — never let the exporter manage DDL; rely on `init-clickhouse.sh`.
2. The `routing/pulse` matcher is the source of truth for which `pulse.type` values get forwarded to the backend. Editing it changes server load — also update server expectations.
3. Athena project tables must match the Parquet schema written by `vector/` (`to_pulse_schema`).
