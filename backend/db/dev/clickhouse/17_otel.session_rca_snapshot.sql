-- Precomputed session-quality RCA rows (ReplacingMergeTree by cached_at).
CREATE TABLE IF NOT EXISTS otel.session_rca_snapshot
(
    ProjectId      LowCardinality(String) CODEC(ZSTD(1)),
    date           Date                   CODEC(Delta, ZSTD(1)),
    window_end_utc DateTime64(3, 'UTC')   COMMENT 'Exclusive upper bound of RCA query window' CODEC(DoubleDelta, ZSTD(1)),
    mode           LowCardinality(String) COMMENT 'hierarchical | flat'                        CODEC(ZSTD(1)),
    baseline       String                 COMMENT 'JSON'                                        CODEC(ZSTD(3)),
    segments       String                 COMMENT 'JSON'                                        CODEC(ZSTD(3)),
    cached_at      DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_window_end window_end_utc TYPE minmax GRANULARITY 1,
    INDEX idx_cached_at  cached_at      TYPE minmax GRANULARITY 1,
    INDEX idx_mode       mode           TYPE set(4) GRANULARITY 1
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(date)
ORDER BY (ProjectId, date, mode)
SETTINGS index_granularity = 8192;
