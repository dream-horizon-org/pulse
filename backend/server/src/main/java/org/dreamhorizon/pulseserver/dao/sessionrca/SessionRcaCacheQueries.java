package org.dreamhorizon.pulseserver.dao.sessionrca;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** SQL for otel.session_rca_cache (ReplacingMergeTree, keyed by ProjectId + date). */
public final class SessionRcaCacheQueries {

  private SessionRcaCacheQueries() {}

  private static final String SELECT_FROM =
      "SELECT ProjectId, date, window_end_utc, mode, baseline, segments, cached_at"
          + " FROM otel.session_rca_cache";

  private static final String INSERT_INTO =
      "INSERT INTO otel.session_rca_cache"
          + " (ProjectId, date, window_end_utc, mode, baseline, segments, cached_at) VALUES ";

  public static String buildSelectByKeyQuery(String projectId, String dateIso) {
    return SELECT_FROM
        + " WHERE ProjectId = '" + escape(projectId) + "'"
        + " AND date = '" + escape(dateIso) + "'";
  }

  public static String buildInsertQuery(
      String projectId,
      String dateIso,
      Instant windowEndExclusiveUtc,
      String mode,
      String baselineJson,
      String segmentsJson,
      LocalDateTime cachedAt) {
    String cachedAtStr =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(cachedAt);
    return INSERT_INTO
        + "('"  + escape(projectId) + "'"
        + ",'"  + escape(dateIso) + "'"
        + ","   + toDateTime64Literal(windowEndExclusiveUtc)
        + ",'"  + escape(mode) + "'"
        + ",'"  + escapeJson(baselineJson) + "'"
        + ",'"  + escapeJson(segmentsJson) + "'"
        + ",toDateTime64('" + escape(cachedAtStr) + "', 3, 'UTC')"
        + ")";
  }

  private static String toDateTime64Literal(Instant instant) {
    String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneOffset.UTC).format(instant);
    return "toDateTime64('" + escape(formatted) + "', 3, 'UTC')";
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }

  private static String escapeJson(String s) {
    if (s == null) {
      return "{}";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
