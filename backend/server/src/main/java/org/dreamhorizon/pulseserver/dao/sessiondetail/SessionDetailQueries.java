package org.dreamhorizon.pulseserver.dao.sessiondetail;

public final class SessionDetailQueries {

  private SessionDetailQueries() {
  }

  public static final String GET_SESSION_CORE = """
      SELECT
        any(SessionId)                                    AS session_id,
        anyIf(UserId, UserId != '')                       AS user_id,
        any(Platform)                                     AS platform,
        any(DeviceModel)                                  AS device,
        any(OsVersion)                                    AS osVersion,
        any(AppVersion)                                   AS appVersion,
        min(Timestamp)                                    AS session_start,
        max(Timestamp)                                    AS session_end,
        toUInt64(
          dateDiff('millisecond', min(Timestamp), max(Timestamp))
        )                                                 AS durationMs,
        any(GeoState)                                     AS geography,
        coalesce(
          round(
            avgIf(
              toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']),
              SpanAttributes['pulse.interaction.apdex_score'] != ''
            ),
            2
          ),
          0
        )                                                 AS qualityScore,
        arrayDistinct(
          arrayFilter(
            x -> x != '',
            arrayMap(
              t -> t.2,
              arraySort(
                t -> t.1,
                groupArray((
                  Timestamp,
                  coalesce(
                    nullIf(trimBoth(SpanAttributes['page.url']), ''),
                    nullIf(trimBoth(SpanAttributes['screen.name']), ''),
                    SpanName
                  )
                ))
              )
            )
          )
        )                                                 AS journey
      FROM otel.otel_traces
      WHERE SessionId = '${session_id}'
      GROUP BY SessionId
      LIMIT 1
      """;

  public static final String GET_SESSION_INTERACTIONS = """
      SELECT
        SpanAttributes['pulse.interaction.name']          AS interaction_name,
        countIf(
          SpanAttributes['pulse.interaction.is_error'] != 'true'
        )                                                 AS success_count,
        countIf(
          SpanAttributes['pulse.interaction.is_error'] = 'true'
        )                                                 AS failure_count,
        round(avgIf(
          toFloat64OrNull(
            SpanAttributes['pulse.interaction.complete_time']
          ) / 1e6,
          SpanAttributes['pulse.interaction.complete_time'] != ''
        ), 2)                                             AS avg_duration_ms,
        round(avgIf(
          toFloat64OrNull(
            SpanAttributes['pulse.interaction.apdex_score']
          ),
          SpanAttributes['pulse.interaction.apdex_score'] != ''
        ), 2)                                             AS apdex_score
      FROM otel.otel_traces
      WHERE SessionId = '${session_id}'
        AND PulseType = 'interaction'
      GROUP BY interaction_name
      """;

  public static final String GET_SESSION_NETWORK = """
      SELECT
        Timestamp                                         AS timestamp,
        Duration                                          AS duration_ns,
        SpanAttributes['http.method']                     AS http_method,
        SpanAttributes['http.url']                        AS http_url,
        SpanAttributes['http.status_code']                AS http_status_code,
        SpanAttributes['http.target']                     AS http_target,
        concat(
          SpanAttributes['http.method'], ' ',
          SpanAttributes['http.url'], ' ',
          SpanAttributes['http.status_code']
        )                                                 AS description,
        TraceId                                           AS traceId,
        SpanId                                            AS spanId
      FROM otel.otel_traces
      WHERE SessionId = '${session_id}'
        AND PulseType LIKE 'network.%'
      ORDER BY Timestamp ASC
      """;

  public static final String GET_SESSION_EVENT_SPANS = """
      SELECT
        Timestamp                                         AS timestamp,
        multiIf(
          PulseType = 'interaction',                      'interaction',
          PulseType = 'app_start',                        'app_start',
          'navigation'
        )                                                 AS event_type,
        multiIf(
          PulseType = 'interaction',
            SpanAttributes['pulse.interaction.name'],
          PulseType = 'app_start',
            SpanAttributes['screen.name'],
          concat(
            SpanAttributes['last.screen.name'],
            ' → ',
            SpanAttributes['screen.name']
          )
        )                                                 AS description,
        Duration                                          AS duration_ns,
        TraceId                                           AS traceId,
        SpanId                                            AS spanId
      FROM otel.otel_traces
      WHERE SessionId = '${session_id}'
        AND (
          PulseType = 'interaction'
          OR PulseType = 'app_start'
          OR mapContains(SpanAttributes, 'last.screen.name')
        )
      ORDER BY Timestamp ASC
      """;

  public static final String GET_SESSION_EXCEPTIONS = """
      SELECT
        Timestamp                                         AS timestamp,
        PulseType                                         AS pulse_type,
        Title                                             AS title,
        ExceptionStackTrace                               AS exception_stack_trace,
        TraceId                                           AS traceId,
        SpanId                                            AS spanId
      FROM otel.stack_trace_events
      WHERE SessionId = '${session_id}'
      ORDER BY Timestamp ASC
      """;

}
