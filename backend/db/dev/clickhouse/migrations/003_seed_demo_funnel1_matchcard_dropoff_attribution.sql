-- Dummy drop-off attribution for MatchCardToMatchDetail (MySQL id=1, project demo-streaming).
-- Main drop: MatchPageLoaded → ClickedBuyPass (UI focusStepIndex 1 → CH StepIndex 2).
-- Re-run safe: deletes prior rows for this seed RunTime first.

ALTER TABLE otel.funnel_dropoff_attribution
    DELETE WHERE ProjectId = 'demo-streaming' AND FunnelId = 1
        AND RunTime = toDateTime64('2026-05-25 12:00:00.000', 3, 'UTC');

ALTER TABLE otel.funnel_results
    DELETE WHERE ProjectId = 'demo-streaming' AND FunnelId = 1
        AND RunTime = toDateTime64('2026-05-25 12:00:00.000', 3, 'UTC');

INSERT INTO otel.funnel_results
    (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)
VALUES
    (1, 'demo-streaming', '2026-05-25 12:00:00.000', 0, 'MatchCardClicked', 41434, 100.0, NULL),
    (1, 'demo-streaming', '2026-05-25 12:00:00.000', 1, 'MatchPageLoaded', 40935, 98.8, 12),
    (1, 'demo-streaming', '2026-05-25 12:00:00.000', 2, 'ClickedBuyPass', 13, 0.03, 180),
    (1, 'demo-streaming', '2026-05-25 12:00:00.000', 3, 'OrderInitiated', 2, 0.005, 45),
    (1, 'demo-streaming', '2026-05-25 12:00:00.000', 4, 'OrderSuccessful', 1, 0.002, 30);

-- StepIndex 2 = drop-off after MatchPageLoaded (GET /dropoffs/1).
INSERT INTO otel.funnel_dropoff_attribution
(
    FunnelId, ProjectId, RunTime, StepIndex,
    CauseKind, CauseKey, CauseLabel,
    DropoffCohort, DropoffAffected, ConverterCohort, ConverterAffected,
    Lift, PValue, ExampleSessions
)
VALUES
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'http_5xx', 'POST api.demo-streaming.example.com/v2/match/buy-pass 503', '503 @ buy-pass API',
    40922, 12276, 13, 0,
    999.0, 0.0001,
    ['mc-sess-503-a', 'mc-sess-503-b', 'mc-sess-503-c']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'http_4xx', 'GET api.demo-streaming.example.com/v2/entitlement/match 403', '403 @ match entitlement',
    40922, 8184, 13, 1,
    630.0, 0.0001,
    ['mc-sess-403-a', 'mc-sess-403-b']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'frozen_frame', 'MatchDetailScreen >3s frozen', 'Frozen frame on match detail',
    40922, 6548, 13, 2,
    252.0, 0.001,
    ['mc-sess-frozen-01', 'mc-sess-frozen-02']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'rage_tap', 'BuyPassButton@MatchDetailScreen', 'Rage tap on Buy Pass CTA',
    40922, 4910, 13, 0,
    378.0, 0.002,
    ['mc-sess-rage-01', 'mc-sess-rage-02', 'mc-sess-rage-03']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'crash', 'IllegalStateException@MatchDetailActivity', 'Crash opening match detail',
    40922, 3274, 13, 0,
    252.0, 0.0001,
    ['mc-sess-crash-01']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'slow_interaction', 'MatchPageLoaded >5s TTI', 'Slow match page load',
    40922, 2455, 13, 3,
    63.0, 0.01,
    ['mc-sess-slow-01']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 2,
    'network_offline', 'offline@match-detail', 'Offline on match detail',
    40922, 1637, 13, 0,
    126.0, 0.005,
    ['mc-sess-offline-01']
);

-- Smaller drop: MatchCardClicked → MatchPageLoaded (GET /dropoffs/0, StepIndex 1).
INSERT INTO otel.funnel_dropoff_attribution
(
    FunnelId, ProjectId, RunTime, StepIndex,
    CauseKind, CauseKey, CauseLabel,
    DropoffCohort, DropoffAffected, ConverterCohort, ConverterAffected,
    Lift, PValue, ExampleSessions
)
VALUES
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 1,
    'http_5xx', 'GET api.demo-streaming.example.com/v2/match/card 502', '502 @ match card API',
    499, 75, 40935, 409,
    7.4, 0.001,
    ['mc-sess-card-502-a']
),
(
    1, 'demo-streaming', '2026-05-25 12:00:00.000', 1,
    'anr', 'ANR@MatchListActivity', 'ANR on match list',
    499, 25, 40935, 4,
    51.2, 0.0001,
    ['mc-sess-anr-card-01']
);
