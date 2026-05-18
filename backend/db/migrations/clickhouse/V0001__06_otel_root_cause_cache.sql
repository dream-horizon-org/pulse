--liquibase formatted sql

--changeset db-migrations:V0001__06_otel_root_cause_cache runOnChange:false failOnError:true splitStatements:true endDelimiter:; dbms:clickhouse
--comment Baseline: otel.root_cause_cache ReplacingMergeTree table for interaction RCA cache.

CREATE TABLE IF NOT EXISTS otel.root_cause_cache
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
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(date)
ORDER BY (ProjectId, interaction_name, date, mode)
SETTINGS index_granularity = 8192;

--rollback empty
