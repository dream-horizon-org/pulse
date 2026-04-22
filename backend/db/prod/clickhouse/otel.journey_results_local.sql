CREATE TABLE IF NOT EXISTS otel.journey_results_local
ON CLUSTER 'pulse-clickhouse'
(
    JourneyId  UInt64                 COMMENT 'MySQL journey.id'                        CODEC(T64, ZSTD(1)),
    ProjectId  LowCardinality(String) COMMENT 'Project ID'                              CODEC(ZSTD(1)),
    RunTime    DateTime64(3, 'UTC')   COMMENT 'Spark job execution time (UTC)'          CODEC(DoubleDelta, ZSTD(1)),
    Direction  LowCardinality(String) COMMENT 'START | END'                             CODEC(ZSTD(1)),
    PosFrom    Int32                  COMMENT 'Source path position; -1 = ENTRY'        CODEC(T64, ZSTD(1)),
    EventFrom  LowCardinality(String) COMMENT 'Event at PosFrom; empty = ENTRY'         CODEC(ZSTD(1)),
    PosTo      Int32                  COMMENT 'Destination path position'               CODEC(T64, ZSTD(1)),
    EventTo    LowCardinality(String) COMMENT 'Event at PosTo'                          CODEC(ZSTD(1)),
    UserCount  UInt64                 COMMENT 'Distinct users or sessions on this edge' CODEC(T64, ZSTD(1)),
    CreatedAt  DateTime64(3, 'UTC')   DEFAULT now64(3) COMMENT 'Row insert time (UTC)'  CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_run_time    RunTime    TYPE minmax GRANULARITY 1,
    INDEX idx_created_at  CreatedAt  TYPE minmax GRANULARITY 1,
    INDEX idx_direction   Direction  TYPE set(2) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/journey_results_local', '{replica}')
PARTITION BY toYYYYMM(toDate(RunTime))
PRIMARY KEY (ProjectId, JourneyId, RunTime)
ORDER BY (ProjectId, JourneyId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';


CREATE TABLE IF NOT EXISTS otel.journey_results
ON CLUSTER 'pulse-clickhouse'
AS otel.journey_results_local
ENGINE = Distributed('pulse-clickhouse', otel, journey_results_local, cityHash64((ProjectId, JourneyId)));
