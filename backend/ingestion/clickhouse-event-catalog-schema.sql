-- =============================================================================
-- ClickHouse: Unified event catalog (Spark EVENTS_INCREMENTAL → ClickHouse)
-- =============================================================================
-- Distinct filter catalog per project. FilterKey identifies the dimension:
--   EVENT            → FilterValue = custom event name (event_name)
--   APP_BUILD_NAME   → FilterValue = app build label (parquet app_build_name)
--   OS_VERSION       → FilterValue = OS version (parquet os_version)
--   OS_NAME          → FilterValue = OS name (parquet os_name)
--
-- Column names: **PascalCase** (consistent with `otel.otel_traces`, funnel/journey results).
--
-- **Engine:** Single-node `ReplacingMergeTree` — local Docker / dev without ZooKeeper.
-- For **multi-replica** production, create `event_catalog_entries_local` +
-- `event_catalog_entries` from `clickhouse-event-catalog-replicated-schema.sql`.
--
-- Populated by backend/spark EventCatalogJob.java.
-- ENGINE ReplacingMergeTree deduplicates rows with the same ORDER BY key on merge;
-- use FINAL in reads when you need a collapsed result set.
--
-- Incremental S3 windows: Spark reads MAX(started_at) from MySQL spark_jobs for the latest
-- SUCCEEDED EVENTS_INCREMENTAL run; if none, scans 7 days of S3.
--
-- If you previously created event_catalog_entries with snake_case columns, migrate:
--   CREATE TABLE otel.event_catalog_entries_new (...); INSERT ...; EXCHANGE TABLES; ...
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
(
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `FilterKey`   LowCardinality(String) COMMENT 'EVENT | APP_BUILD_NAME | OS_VERSION | OS_NAME',
    `FilterValue` String                  COMMENT 'Distinct value for that filter key'
)
ENGINE = ReplacingMergeTree
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;

-- ---------------------------------------------------------------------------
-- Legacy tables (deprecated — drop after backfill/migration if they exist)
-- ---------------------------------------------------------------------------
-- otel.event_catalog
-- otel.event_filter_values

-- ---------------------------------------------------------------------------
-- Example queries (use FINAL for deduplicated keys)
-- ---------------------------------------------------------------------------

-- Event names
-- SELECT FilterValue FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'EVENT' ORDER BY FilterValue;
-- App build names
-- SELECT FilterValue FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'APP_BUILD_NAME' ORDER BY FilterValue;
