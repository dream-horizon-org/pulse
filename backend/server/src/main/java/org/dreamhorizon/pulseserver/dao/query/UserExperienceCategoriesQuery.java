package org.dreamhorizon.pulseserver.dao.query;

public class UserExperienceCategoriesQuery {
  public static final String GET_SESSIONS_QUERY = """
      WITH
      (\
      SELECT groupUniqArray(SessionId) AS ids
      FROM otel_traces
      WHERE SpanName = '${span_name}'
      ) AS session_ids
      SELECT
      SessionId,
      DeviceModel,
      UserId,
      LogAttributes['Duration']    AS duration,
      LogAttributes['has_anr']     AS hasAnr,
      LogAttributes['has_crash']   AS hasCrash,
      LogAttributes['has_network'] AS hasNetwork,
      LogAttributes['has_frozen']  AS hasFrozen,
      Timestamp
      FROM otel_logs
      WHERE
      has(session_ids, SessionId)
      AND Timestamp >= toDateTime64('${start_time}', 9, 'UTC')
      AND Timestamp <= toDateTime64('${end_time}', 9, 'UTC')
      ${app_version_filter}
      ${platform_filter}
      ${os_version_filter}
      ${network_provider_filter}
      ${state_filter};
      """;

}