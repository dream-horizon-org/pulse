-- =============================================================================
-- Test data for Session Detail API
-- Session ID : test-session-detail-001
-- Project    : fancode
-- Time window: 2026-03-10 10:00 – 10:30 UTC
-- =============================================================================

-- Shared resource/span attribute fragments used across inserts
-- ResourceAttributes for every span in this session:
--   project.id       = fancode
--   app.build_name   = 9.4.0_10960287
--   os.name          = Android
--   os.version       = 14
--   device.model.name= Pixel 8
--   rum.sdk.version  = 0.0.7-alpha
--   service.name     = FC-Local

-- Common SpanAttributes for every span:
--   session.id = test-session-detail-001
--   user.id    = user-42
--   geo.region.iso_code = MH  (Maharashtra)

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. App Start span  (event_type = app_start)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.otel_traces (
  Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
  ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
  Duration, StatusCode, StatusMessage,
  `Events.Timestamp`, `Events.Name`, `Events.Attributes`,
  `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`
) VALUES (
  '2026-03-10 10:00:01.000000000',
  'aaaa0001000000000000000000000001', '1000000000000001', '0000000000000000', '',
  'AppStart', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.app', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'app_start','screen.name':'SplashActivity'},
  850000000, 'Ok', '',
  [], [], [], [], [], [], []
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Navigation spans  (has last.screen.name → event_type = navigation)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.otel_traces (
  Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
  ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
  Duration, StatusCode, StatusMessage,
  `Events.Timestamp`, `Events.Name`, `Events.Attributes`,
  `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`
) VALUES
-- Splash → Home
(
  '2026-03-10 10:00:02.500000000',
  'aaaa0001000000000000000000000001', '1000000000000002', '1000000000000001', '',
  'Navigation', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.navigation', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','screen.name':'HomeScreen','last.screen.name':'SplashActivity'},
  120000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- Home → MatchList
(
  '2026-03-10 10:05:00.000000000',
  'aaaa0001000000000000000000000002', '1000000000000003', '1000000000000001', '',
  'Navigation', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.navigation', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','screen.name':'MatchListScreen','last.screen.name':'HomeScreen'},
  95000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- MatchList → MatchDetail
(
  '2026-03-10 10:12:00.000000000',
  'aaaa0001000000000000000000000003', '1000000000000004', '1000000000000001', '',
  'Navigation', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.navigation', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','screen.name':'MatchDetailScreen','last.screen.name':'MatchListScreen'},
  110000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- MatchDetail → Home (back navigation)
(
  '2026-03-10 10:25:00.000000000',
  'aaaa0001000000000000000000000004', '1000000000000005', '1000000000000001', '',
  'Navigation', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.navigation', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','screen.name':'HomeScreen','last.screen.name':'MatchDetailScreen'},
  80000000, 'Ok', '',
  [], [], [], [], [], [], []
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Interaction spans  (PulseType = interaction)
--    Each interaction appears twice for aggregation testing (success + failure)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.otel_traces (
  Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
  ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
  Duration, StatusCode, StatusMessage,
  `Events.Timestamp`, `Events.Name`, `Events.Attributes`,
  `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`
) VALUES
-- FetchMatches - success 1
(
  '2026-03-10 10:03:00.000000000',
  'aaaa0001000000000000000000000005', '2000000000000001', '1000000000000002', '',
  'FetchMatches', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.interaction', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'interaction','pulse.interaction.name':'FetchMatches','pulse.interaction.is_error':'false','pulse.interaction.complete_time':'450000000','pulse.interaction.apdex_score':'0.95','screen.name':'HomeScreen'},
  450000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- FetchMatches - success 2
(
  '2026-03-10 10:06:00.000000000',
  'aaaa0001000000000000000000000006', '2000000000000002', '1000000000000003', '',
  'FetchMatches', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.interaction', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'interaction','pulse.interaction.name':'FetchMatches','pulse.interaction.is_error':'false','pulse.interaction.complete_time':'380000000','pulse.interaction.apdex_score':'0.98','screen.name':'MatchListScreen'},
  380000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- FetchMatches - failure
(
  '2026-03-10 10:08:00.000000000',
  'aaaa0001000000000000000000000007', '2000000000000003', '1000000000000003', '',
  'FetchMatches', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.interaction', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'interaction','pulse.interaction.name':'FetchMatches','pulse.interaction.is_error':'true','pulse.interaction.complete_time':'5200000000','pulse.interaction.apdex_score':'0.10','screen.name':'MatchListScreen'},
  5200000000, 'Error', 'Timeout',
  [], [], [], [], [], [], []
),
-- LoadMatchDetail - success
(
  '2026-03-10 10:13:00.000000000',
  'aaaa0001000000000000000000000008', '2000000000000004', '1000000000000004', '',
  'LoadMatchDetail', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.interaction', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'interaction','pulse.interaction.name':'LoadMatchDetail','pulse.interaction.is_error':'false','pulse.interaction.complete_time':'600000000','pulse.interaction.apdex_score':'0.88','screen.name':'MatchDetailScreen'},
  600000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- LoadMatchDetail - failure
(
  '2026-03-10 10:15:00.000000000',
  'aaaa0001000000000000000000000009', '2000000000000005', '1000000000000004', '',
  'LoadMatchDetail', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.interaction', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'interaction','pulse.interaction.name':'LoadMatchDetail','pulse.interaction.is_error':'true','pulse.interaction.complete_time':'8000000000','pulse.interaction.apdex_score':'0.05','screen.name':'MatchDetailScreen'},
  8000000000, 'Error', 'ServerError',
  [], [], [], [], [], [], []
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Network request spans  (PulseType LIKE 'network.%')
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.otel_traces (
  Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
  ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
  Duration, StatusCode, StatusMessage,
  `Events.Timestamp`, `Events.Name`, `Events.Attributes`,
  `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`
) VALUES
-- GET /api/v1/matches  200
(
  '2026-03-10 10:03:01.000000000',
  'aaaa0001000000000000000000000005', '3000000000000001', '2000000000000001', '',
  'HTTP GET', 'Client', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.http', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'network.request','http.method':'GET','http.url':'https://api.fancode.com/api/v1/matches','http.status_code':'200','http.target':'/api/v1/matches'},
  320000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- GET /api/v1/matches  timeout
(
  '2026-03-10 10:08:01.000000000',
  'aaaa0001000000000000000000000007', '3000000000000002', '2000000000000003', '',
  'HTTP GET', 'Client', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.http', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'network.request','http.method':'GET','http.url':'https://api.fancode.com/api/v1/matches','http.status_code':'504','http.target':'/api/v1/matches'},
  5000000000, 'Error', 'Gateway Timeout',
  [], [], [], [], [], [], []
),
-- POST /api/v1/match/12345/subscribe  201
(
  '2026-03-10 10:14:00.000000000',
  'aaaa0001000000000000000000000008', '3000000000000003', '2000000000000004', '',
  'HTTP POST', 'Client', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.http', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'network.request','http.method':'POST','http.url':'https://api.fancode.com/api/v1/match/12345/subscribe','http.status_code':'201','http.target':'/api/v1/match/12345/subscribe'},
  150000000, 'Ok', '',
  [], [], [], [], [], [], []
),
-- GET /api/v1/match/12345/detail  500
(
  '2026-03-10 10:15:01.000000000',
  'aaaa0001000000000000000000000009', '3000000000000004', '2000000000000005', '',
  'HTTP GET', 'Client', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.http', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'network.request','http.method':'GET','http.url':'https://api.fancode.com/api/v1/match/12345/detail','http.status_code':'500','http.target':'/api/v1/match/12345/detail'},
  2800000000, 'Error', 'Internal Server Error',
  [], [], [], [], [], [], []
),
-- GET /api/v1/user/profile  200
(
  '2026-03-10 10:00:03.000000000',
  'aaaa0001000000000000000000000001', '3000000000000005', '1000000000000001', '',
  'HTTP GET', 'Client', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.http', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','pulse.type':'network.request','http.method':'GET','http.url':'https://api.fancode.com/api/v1/user/profile','http.status_code':'200','http.target':'/api/v1/user/profile'},
  180000000, 'Ok', '',
  [], [], [], [], [], [], []
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Lifecycle / noise spans  (PulseType = '' or irrelevant)
--    These contribute to session_start, session_end, journey, and quality score
--    but should NOT appear in the events timeline
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.otel_traces (
  Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
  ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
  Duration, StatusCode, StatusMessage,
  `Events.Timestamp`, `Events.Name`, `Events.Attributes`,
  `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`
) VALUES
-- Lifecycle: Created
(
  '2026-03-10 10:00:00.500000000',
  'aaaa0001000000000000000000000001', '9000000000000001', '0000000000000000', '',
  'Created', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.lifecycle', '',
  {'session.id':'test-session-detail-001','user.id':'','geo.region.iso_code':'MH','screen.name':'SplashActivity'},
  15000000, 'Unset', '',
  [], [], [], [], [], [], []
),
-- Lifecycle: Destroyed (last span in session — sets session_end)
(
  '2026-03-10 10:30:00.000000000',
  'aaaa0001000000000000000000000010', '9000000000000002', '0000000000000000', '',
  'Destroyed', 'Internal', 'FC-Local',
  {'project.id':'fancode','app.build_name':'9.4.0_10960287','os.name':'Android','os.version':'14','device.model.name':'Pixel 8','rum.sdk.version':'0.0.7-alpha','service.name':'FC-Local'},
  'io.opentelemetry.lifecycle', '',
  {'session.id':'test-session-detail-001','user.id':'user-42','geo.region.iso_code':'MH','screen.name':'HomeScreen'},
  10000000, 'Unset', '',
  [], [], [], [], [], [], []
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Exceptions in stack_trace_events  (ANR + Crash)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO otel.stack_trace_events (
  Timestamp, EventName, Title,
  ExceptionStackTrace, ExceptionStackTraceRaw, ExceptionMessage, ExceptionType,
  Interactions, ScreenName, UserId, SessionId,
  Platform, OsVersion, DeviceModel, AppVersionCode, AppVersion, SdkVersion, BundleId,
  TraceId, SpanId, GroupId, Signature, Fingerprint,
  ScopeAttributes, LogAttributes, ResourceAttributes
) VALUES
-- ANR on MatchDetailScreen
(
  '2026-03-10 10:16:00.000000000',
  'ANR', 'Application Not Responding - MatchDetailScreen',
  'android.os.MessageQueue.nativePollOnce(Native Method)\n  android.os.MessageQueue.next(MessageQueue.java:335)\n  android.os.Looper.loopOnce(Looper.java:186)\n  android.os.Looper.loop(Looper.java:313)\n  android.app.ActivityThread.main(ActivityThread.java:8751)',
  'android.os.MessageQueue.nativePollOnce(Native Method)\n  android.os.MessageQueue.next(MessageQueue.java:335)',
  'Input dispatching timed out (MatchDetailScreen)',
  'ANR',
  ['LoadMatchDetail'],
  'MatchDetailScreen',
  'user-42',
  'test-session-detail-001',
  'Android', '14', 'Pixel 8', '10960287', '9.4.0_10960287', '0.0.7-alpha', 'com.fancode',
  'aaaa0001000000000000000000000008', '2000000000000004',
  'anr-group-001', 'anr-sig-001', 'anr-fp-001',
  {}, {'pulse.type':'anr'}, {'project.id':'fancode'}
),
-- Crash — NullPointerException
(
  '2026-03-10 10:20:00.000000000',
  'Crash', 'NullPointerException in MatchDetailPresenter',
  'java.lang.NullPointerException: Attempt to invoke virtual method void com.fc.match.MatchDetailPresenter.onScoreUpdate(com.fc.model.Score) on a null object reference\n  at com.fc.match.MatchDetailFragment.handleScore(MatchDetailFragment.java:142)\n  at com.fc.match.MatchDetailFragment.onEvent(MatchDetailFragment.java:98)\n  at com.fc.event.EventBus.dispatch(EventBus.java:52)',
  'java.lang.NullPointerException: Attempt to invoke virtual method void com.fc.match.MatchDetailPresenter.onScoreUpdate',
  'Attempt to invoke virtual method void com.fc.match.MatchDetailPresenter.onScoreUpdate(com.fc.model.Score) on a null object reference',
  'NullPointerException',
  ['LoadMatchDetail'],
  'MatchDetailScreen',
  'user-42',
  'test-session-detail-001',
  'Android', '14', 'Pixel 8', '10960287', '9.4.0_10960287', '0.0.7-alpha', 'com.fancode',
  'aaaa0001000000000000000000000009', '2000000000000005',
  'crash-group-001', 'crash-sig-001', 'crash-fp-001',
  {}, {'pulse.type':'crash'}, {'project.id':'fancode'}
);

-- =============================================================================
-- Verification queries — run these after inserting to validate
-- =============================================================================

-- Core metadata (should return 1 row)
-- SELECT any(SessionId) AS session_id, anyIf(UserId, UserId != '') AS user_id,
--        any(Platform) AS platform, any(DeviceModel) AS device,
--        any(OsVersion) AS osVersion, any(AppVersion) AS appVersion,
--        min(Timestamp) AS session_start, max(Timestamp) AS session_end,
--        dateDiff('millisecond', min(Timestamp), max(Timestamp)) AS durationMs
-- FROM otel.otel_traces
-- WHERE SessionId = 'test-session-detail-001'
-- GROUP BY SessionId;

-- Interactions (should return 2 rows: FetchMatches and LoadMatchDetail)
-- SELECT SpanAttributes['pulse.interaction.name'] AS name,
--        countIf(SpanAttributes['pulse.interaction.is_error'] != 'true') AS success,
--        countIf(SpanAttributes['pulse.interaction.is_error'] = 'true') AS failure
-- FROM otel.otel_traces
-- WHERE SessionId = 'test-session-detail-001' AND PulseType = 'interaction'
-- GROUP BY name;

-- Network requests (should return 5 rows)
-- SELECT Timestamp, SpanAttributes['http.method'], SpanAttributes['http.url'], SpanAttributes['http.status_code']
-- FROM otel.otel_traces
-- WHERE SessionId = 'test-session-detail-001' AND PulseType LIKE 'network.%'
-- ORDER BY Timestamp;

-- Event spans (should return app_start + navigations + interactions = 1+4+5 = 10)
-- SELECT Timestamp,
--        multiIf(PulseType='interaction','interaction',PulseType='app_start','app_start','navigation') AS event_type
-- FROM otel.otel_traces
-- WHERE SessionId = 'test-session-detail-001'
--   AND (PulseType = 'interaction' OR PulseType = 'app_start' OR mapContains(SpanAttributes, 'last.screen.name'))
-- ORDER BY Timestamp;

-- Exceptions (should return 2 rows: ANR + Crash)
-- SELECT Timestamp, PulseType, Title, ExceptionType
-- FROM otel.stack_trace_events
-- WHERE SessionId = 'test-session-detail-001'
-- ORDER BY Timestamp;
