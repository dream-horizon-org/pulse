-- Copy all spans for MatchCardClickedToMatchDetailLoaded onto yesterday (same time-of-day).
-- Re-run safe: deletes prior copy (TraceId prefix) then inserts.
--
-- docker exec -i pulse-clickhouse clickhouse-client --multiquery < deploy/db/seed/clickhouse_match_card_freq_yesterday.sql

ALTER TABLE otel.otel_traces DELETE WHERE startsWith(TraceId, 'pulsefreqcopy-') SETTINGS mutations_sync = 1;

INSERT INTO otel.otel_traces (
  Timestamp,
  TraceId,
  SpanId,
  ParentSpanId,
  TraceState,
  SpanName,
  SpanKind,
  ServiceName,
  ResourceAttributes,
  ScopeName,
  ScopeVersion,
  SpanAttributes,
  Duration,
  StatusCode,
  StatusMessage,
  `Events.Timestamp`,
  `Events.Name`,
  `Events.Attributes`,
  `Links.TraceId`,
  `Links.SpanId`,
  `Links.TraceState`,
  `Links.Attributes`
)
SELECT
  addDays(Timestamp, dateDiff('day', toDate(Timestamp), yesterday())),
  concat('pulsefreqcopy-', TraceId),
  SpanId,
  ParentSpanId,
  TraceState,
  SpanName,
  SpanKind,
  ServiceName,
  ResourceAttributes,
  ScopeName,
  ScopeVersion,
  SpanAttributes,
  Duration,
  StatusCode,
  StatusMessage,
  `Events.Timestamp`,
  `Events.Name`,
  `Events.Attributes`,
  `Links.TraceId`,
  `Links.SpanId`,
  `Links.TraceState`,
  `Links.Attributes`
FROM otel.otel_traces
WHERE SpanAttributes['pulse.interaction.name'] = 'MatchCardClickedToMatchDetailLoaded';
