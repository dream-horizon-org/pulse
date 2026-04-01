-- =============================================================================
-- ClickHouse: Unified event catalog (Spark EVENTS_INCREMENTAL → ClickHouse)
-- =============================================================================
-- Distinct custom event names per project. All rows use FilterKey = 'EVENT';
-- FilterValue is the event name.
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
    `FilterKey`   LowCardinality(String) COMMENT 'Always EVENT for this table',
    `FilterValue` String                  COMMENT 'Custom event name'
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

-- All event names for a project
-- SELECT FilterValue
-- FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'EVENT'
-- ORDER BY FilterValue;

-- Distinct non-EVENT filter keys (GET /v1/funnel/events → filterKeys)
-- SELECT DISTINCT FilterKey
-- FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey != 'EVENT'
-- ORDER BY FilterKey;

-- Values for one filter key (GET /v1/funnel/filters/{filterKey}/values)
-- SELECT DISTINCT FilterValue
-- FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'OS'
-- ORDER BY FilterValue;
