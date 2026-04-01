-- =============================================================================
-- ClickHouse: Event catalog — REPLICATED cluster DDL
-- =============================================================================
-- Use on a **ClickHouse cluster** with ZooKeeper / ClickHouse Keeper and macros
-- `{shard}`, `{replica}` (see deploy/terraform/clickhouse macros).
--
-- Creates:
--   * otel.event_catalog_entries_local — ReplicatedReplacingMergeTree (dedup on merge)
--   * otel.event_catalog_entries — Distributed (Spark / API target; same name as dev)
--
-- Column names: **PascalCase** (same as `clickhouse-event-catalog-schema.sql`).
--
-- **Do not** run on single-node Docker dev (no cluster / no Keeper) — use
-- `clickhouse-event-catalog-schema.sql` instead.
--
-- Shard routing: `cityHash64(ProjectId)` keeps a project’s catalog rows on one shard.
-- Reads that need a deduplicated key set: `SELECT ... FROM otel.event_catalog_entries FINAL`.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS otel ON CLUSTER `pulse-clickhouse`;

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries_local
ON CLUSTER `pulse-clickhouse`
(
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `FilterKey`   LowCardinality(String) COMMENT 'Always EVENT for this table',
    `FilterValue` String                  COMMENT 'Custom event name'
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
