package org.dreamhorizon.pulseserver.dao.query;

public class UserExperienceCategoriesQuery {
  public static final String GET_SESSIONS_QUERY = "WITH\n" +
      "(" +
      "SELECT groupUniqArray(SessionId) AS ids\n" +
      "FROM otel_traces\n" +
      "WHERE SpanName = '${span_name}'\n" +
      ") AS session_ids\n" +
      "SELECT\n" +
      "SessionId,\n" +
      "DeviceModel,\n" +
      "UserId,\n" +
      "LogAttributes['Duration']    AS duration,\n" +
      "LogAttributes['has_anr']     AS hasAnr,\n" +
      "LogAttributes['has_crash']   AS hasCrash,\n" +
      "LogAttributes['has_network'] AS hasNetwork,\n" +
      "LogAttributes['has_frozen']  AS hasFrozen,\n" +
      "Timestamp\n" +
      "FROM otel_logs\n" +
      "WHERE\n" +
      "has(session_ids, SessionId)\n" +
      "AND Timestamp >= toDateTime64('${start_time}', 9, 'UTC')\n" +
      "AND Timestamp <= toDateTime64('${end_time}', 9, 'UTC')\n" +
      "${app_version_filter}\n" +
      "${platform_filter}\n" +
      "${os_version_filter}\n" +
      "${network_provider_filter}\n" +
      "${state_filter};\n";

}