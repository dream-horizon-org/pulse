-- Extra spans for MatchCardClickedToMatchDetailLoaded: Z_* has highest count (600 vs 100 vs 100)
-- so RCA heatmap picks Z first even vs other seeded rows on the same day.
-- docker exec -i pulse-clickhouse clickhouse-client --multiquery < deploy/db/seed/clickhouse_z_screen_highest_freq.sql

ALTER TABLE otel.otel_traces DELETE WHERE startsWith(TraceId, 'pulsezfreq-') SETTINGS mutations_sync = 1;

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
WITH template AS (
  SELECT *
  FROM otel.otel_traces
  WHERE SpanAttributes['pulse.interaction.name'] = 'MatchCardClickedToMatchDetailLoaded'
    AND NOT startsWith(TraceId, 'pulsefreqcopy-')
    AND NOT startsWith(TraceId, 'pulsezfreq-')
  LIMIT 1
)
SELECT
  toDateTime64(toStartOfDay(yesterday()) + toIntervalSecond(600 + number), 9, 'UTC'),
  concat('pulsezfreq-', lower(hex(MD5(toString(number))))),
  substring(hex(sipHash128(concat('zspan', toString(number)))), 1, 16),
  template.ParentSpanId,
  template.TraceState,
  template.SpanName,
  template.SpanKind,
  template.ServiceName,
  template.ResourceAttributes,
  template.ScopeName,
  template.ScopeVersion,
  mapUpdate(
    template.SpanAttributes,
    map(
      'screen.name',
      multiIf(
        number < 600,
        'Z_MostFrequentScreen_Test',
        number < 700,
        'A_LessFrequentScreen_Test',
        'M_MediumFrequentScreen_Test'
      )
    )
  ),
  template.Duration,
  template.StatusCode,
  template.StatusMessage,
  template.`Events.Timestamp`,
  template.`Events.Name`,
  template.`Events.Attributes`,
  template.`Links.TraceId`,
  template.`Links.SpanId`,
  template.`Links.TraceState`,
  template.`Links.Attributes`
FROM template
CROSS JOIN numbers(800) AS n;
