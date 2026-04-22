CREATE TABLE IF NOT EXISTS otel.event_catalog_entries_local
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId    LowCardinality(String) COMMENT 'Project ID'                                                  CODEC(ZSTD(1)),
    FilterKey    LowCardinality(String) COMMENT 'Filter dimension — EVENT | APP_BUILD_NAME | OS_NAME | OS_VERSION' CODEC(ZSTD(1)),
    FilterValue  String                 COMMENT 'Distinct value for the filter dimension'                     CODEC(ZSTD(1))
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/event_catalog_entries_local', '{replica}')
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;


CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
ON CLUSTER 'pulse-clickhouse'
AS otel.event_catalog_entries_local
ENGINE = Distributed('pulse-clickhouse', otel, event_catalog_entries_local, cityHash64(ProjectId));