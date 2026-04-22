CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
(
    ProjectId    LowCardinality(String) COMMENT 'Project ID'                                                  CODEC(ZSTD(1)),
    FilterKey    LowCardinality(String) COMMENT 'Filter dimension — EVENT | APP_BUILD_NAME | OS_NAME | OS_VERSION' CODEC(ZSTD(1)),
    FilterValue  String                 COMMENT 'Distinct value for the filter dimension'                     CODEC(ZSTD(1))
)
ENGINE = ReplacingMergeTree
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;