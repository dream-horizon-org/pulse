-- Dummy drop-off attribution for local funnel RCA / drop-off panel testing.
-- Funnel: MySQL id=5 (SectionContentClickedToPLAY), project fancode, focus step 0 → StepIndex 1.
-- Re-run safe: deletes prior rows for this seed RunTime first.

-- Shared run timestamp (must match funnel_results seed below).
-- UI/RCA with no runTime query param uses max(RunTime) from funnel_results.

CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    FunnelId           UInt64,
    ProjectId          LowCardinality(String),
    RunTime            DateTime64(3, 'UTC'),
    StepIndex          UInt8,
    StepName           LowCardinality(String),
    UserCount          UInt64,
    ConversionPct      Float64,
    MedianStepSeconds  Nullable(Int64),
    CreatedAt          DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex)
SETTINGS index_granularity = 8192;

ALTER TABLE otel.funnel_dropoff_attribution
    DELETE WHERE ProjectId = 'fancode' AND FunnelId = 5
        AND RunTime = toDateTime64('2026-05-25 12:00:00.000', 3, 'UTC');

ALTER TABLE otel.funnel_results
    DELETE WHERE ProjectId = 'fancode' AND FunnelId = 5
        AND RunTime = toDateTime64('2026-05-25 12:00:00.000', 3, 'UTC');

INSERT INTO otel.funnel_results
    (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)
VALUES
    (5, 'fancode', '2026-05-25 12:00:00.000', 0, 'SectionContentClicked', 10000, 100.0, NULL),
    (5, 'fancode', '2026-05-25 12:00:00.000', 1, 'PLAY', 6200, 62.0, 45);

-- StepIndex 1 = drop-off after step 0 (focusStepIndex 0). Lift ≈ (DA/DC) / (CA/CC).
INSERT INTO otel.funnel_dropoff_attribution
(
    FunnelId, ProjectId, RunTime, StepIndex,
    CauseKind, CauseKey, CauseLabel,
    DropoffCohort, DropoffAffected, ConverterCohort, ConverterAffected,
    Lift, PValue, ExampleSessions
)
VALUES
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'http_5xx', 'POST api.fancode.com/v1/play 503', '503 @ play API',
    3800, 456, 6200, 62,
    2.41, 0.001,
    ['local-sess-501', 'local-sess-502', 'local-sess-503']
),
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'crash', 'NullPointerException@PlayerScreen', 'Crash on PlayerScreen',
    3800, 304, 6200, 31,
    4.02, 0.0001,
    ['local-sess-crash-01', 'local-sess-crash-02']
),
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'frozen_frame', 'PlayerScreen >3s frozen', 'Frozen frame on PlayerScreen',
    3800, 228, 6200, 62,
    1.21, 0.02,
    ['local-sess-frozen-01']
),
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'http_4xx', 'GET api.fancode.com/v1/entitlement 403', '403 @ entitlement check',
    3800, 152, 6200, 31,
    2.01, 0.005,
    ['local-sess-403-a', 'local-sess-403-b']
),
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'anr', 'ANR@PlayerActivity', 'ANR in player',
    3800, 76, 6200, 6,
    6.58, 0.0001,
    ['local-sess-anr-01']
),
(
    5, 'fancode', '2026-05-25 12:00:00.000', 1,
    'non_fatal', 'PlaybackStall@PlayerScreen', 'Non-fatal playback stall',
    3800, 114, 6200, 62,
    0.60, 0.15,
    ['local-sess-nf-01']
);

-- Optional: step 1 → step 2 drop-off (focusStepIndex 1) for multi-step testing.
INSERT INTO otel.funnel_dropoff_attribution
(
    FunnelId, ProjectId, RunTime, StepIndex,
    CauseKind, CauseKey, CauseLabel,
    DropoffCohort, DropoffAffected, ConverterCohort, ConverterAffected,
    Lift, PValue, ExampleSessions
)
VALUES
(
    5, 'fancode', '2026-05-25 12:00:00.000', 2,
    'network_offline', 'offline@playback', 'Offline during playback',
    1200, 180, 6200, 62,
    1.49, 0.01,
    ['local-sess-offline-01']
);
