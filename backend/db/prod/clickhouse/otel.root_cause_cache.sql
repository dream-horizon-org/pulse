CREATE TABLE otel.root_cause_cache_local
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId         LowCardinality(String) CODEC(ZSTD(1)),
    interaction_name  LowCardinality(String) CODEC(ZSTD(1)),
    date              Date                   CODEC(Delta, ZSTD(1)),
    window_end_utc    DateTime64(3, 'UTC')   COMMENT 'Exclusive upper bound of RCA query window' CODEC(DoubleDelta, ZSTD(1)),
    mode              LowCardinality(String) COMMENT 'hierarchical | flat'                        CODEC(ZSTD(1)),
    baseline          String                 COMMENT 'JSON'                                        CODEC(ZSTD(3)),
    segments          String                 COMMENT 'JSON'                                        CODEC(ZSTD(3)),
    cached_at         DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_window_end window_end_utc TYPE minmax GRANULARITY 1,
    INDEX idx_cached_at  cached_at      TYPE minmax GRANULARITY 1,
    INDEX idx_mode       mode           TYPE set(4) GRANULARITY 1
    )
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/root_cause_cache', '{replica}', cached_at)
PARTITION BY toYYYYMM(date)
ORDER BY (ProjectId, interaction_name, date, mode)
TTL toDateTime(date) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(date) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';


CREATE TABLE IF NOT EXISTS otel.root_cause_cache
ON CLUSTER 'pulse-clickhouse'
AS otel.root_cause_cache_local
    ENGINE = Distributed('pulse-clickhouse', otel, root_cause_cache_local, cityHash64((ProjectId, interaction_name)));
