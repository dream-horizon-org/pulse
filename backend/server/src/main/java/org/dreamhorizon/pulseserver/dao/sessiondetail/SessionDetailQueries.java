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
        )                                                 AS qualityScore
      FROM otel.otel_traces
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
      GROUP BY SessionId
      LIMIT 1
      """;

  public static final String GET_SESSION_JOURNEY = """
      SELECT
        toJSONString(
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
                      nullIf(trimBoth(ifNull(SpanAttributes['screen.name'], '')), ''),
                      SpanName
                    )
                  ))
                )
              )
            )
          )
        )                                                 AS journey
      FROM otel.otel_traces
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
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
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND PulseType = 'interaction'
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
      GROUP BY interaction_name
      """;

  public static final String GET_SESSION_NETWORK = """
      SELECT
        Timestamp                                         AS timestamp,
        Duration                                          AS duration_ns,
        HttpMethod                                        AS http_method,
        HttpUrl                                           AS http_url,
        HttpStatusCode                                    AS http_status_code,
        SpanAttributes['http.target']                     AS http_target,
        concat(
          HttpMethod, ' ',
          HttpUrl, ' ',
          HttpStatusCode
        )                                                 AS description,
        TraceId                                           AS traceId,
        SpanId                                            AS spanId
      FROM otel.otel_traces
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND PulseType LIKE 'network.%'
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
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
            ifNull(SpanAttributes['screen.name'], ''),
          concat(
            SpanAttributes['last.screen.name'],
            ' → ',
            ifNull(SpanAttributes['screen.name'], '')
          )
        )                                                 AS description,
        Duration                                          AS duration_ns,
        TraceId                                           AS traceId,
        SpanId                                            AS spanId
      FROM otel.otel_traces
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND (
          PulseType = 'interaction'
          OR PulseType = 'app_start'
          OR mapContains(SpanAttributes, 'last.screen.name')
        )
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
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
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
        AND Timestamp >= parseDateTime64BestEffort('${session_start}', 9, 'UTC') - INTERVAL 10 MINUTE
        AND Timestamp <= parseDateTime64BestEffort('${session_end}', 9, 'UTC') + INTERVAL 10 MINUTE
      ORDER BY Timestamp ASC
      """;

}
