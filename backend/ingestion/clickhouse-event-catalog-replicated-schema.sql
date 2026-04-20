-- =============================================================================
-- ClickHouse: Event catalog — REPLICATED cluster DDL
-- =============================================================================
-- Use on a **ClickHouse cluster** with ZooKeeper / ClickHouse Keeper and macros
-- `{shard}`, `{replica}` (see deploy/terraform/clickhouse macros).
--
-- Creates:
--   * otel.event_catalog_entries_local — ReplicatedReplacingMergeTree (dedup on merge)
--   * otel.event_catalog_entries — Distributed (Spark / API target; same name as dev)
--   * otel.event_catalog_entries_mv — Materialized view → `event_catalog_entries_local`
--     from `otel.otel_logs_local` (custom_event rows; same FilterKey mapping as dev script)
--
-- Column names: **PascalCase** (same as `clickhouse-event-catalog-schema.sql`).
--
-- **Do not** run on single-node Docker dev (no cluster / no Keeper) — use
-- `clickhouse-event-catalog-schema.sql` instead.
--
-- Shard routing: `cityHash64(ProjectId)` keeps a project’s catalog rows on one shard.
-- Reads that need a deduplicated key set: `SELECT ... FROM otel.event_catalog_entries FINAL`.
--
-- **Apply MV after** `otel.otel_logs_local` exists (e.g. `clickhouse-replicated-tiered-schema.sql`).
-- =============================================================================

CREATE DATABASE IF NOT EXISTS otel ON CLUSTER `pulse-clickhouse`;

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries_local
ON CLUSTER `pulse-clickhouse`
(
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `FilterKey`   LowCardinality(String) COMMENT 'EVENT | APP_BUILD_NAME | OS_VERSION | OS_NAME',
    `FilterValue` String                  COMMENT 'Distinct value for that filter key'
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/otel/event_catalog_entries_local',
    '{replica}'
)
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
ON CLUSTER `pulse-clickhouse`
AS otel.event_catalog_entries_local
ENGINE = Distributed(`pulse-clickhouse`, otel, event_catalog_entries_local, cityHash64(ProjectId));

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv
ON CLUSTER `pulse-clickhouse`
TO otel.event_catalog_entries_local
AS
SELECT
    ProjectId,
    CAST('EVENT' AS LowCardinality(String)) AS FilterKey,
    Body AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND Body != ''
UNION ALL
SELECT
    ProjectId,
    CAST('APP_BUILD_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['app.build_name'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['app.build_name'], '') != ''
UNION ALL
SELECT
    ProjectId,
    CAST('OS_VERSION' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.version'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.version'], '') != ''
UNION ALL
SELECT
    ProjectId,
    CAST('OS_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.name'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.name'], '') != '';
