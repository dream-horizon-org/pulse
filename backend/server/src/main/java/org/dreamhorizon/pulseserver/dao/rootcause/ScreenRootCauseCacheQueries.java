package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** SQL for otel.screen_root_cause_cache (ReplacingMergeTree). */
public final class ScreenRootCauseCacheQueries {

  private ScreenRootCauseCacheQueries() {}

  public static final String SELECT_FROM_SCREEN_ROOT_CAUSE_CACHE =
      "SELECT ProjectId, screen_name, window_start_date, window_end_date, window_start_utc,"
          + " window_end_utc, result_json, cached_at"
          + " FROM otel.screen_root_cause_cache";

  public static final String INSERT_INTO_SCREEN_ROOT_CAUSE_CACHE =
      "INSERT INTO otel.screen_root_cause_cache (ProjectId, screen_name, window_start_date,"
          + " window_end_date, window_start_utc, window_end_utc, result_json, cached_at) VALUES ";

  public static String buildSelectByKeyQuery(
      final String projectId,
      final String screenName,
      final String windowStartDateIso,
      final String windowEndDateIso) {
    return SELECT_FROM_SCREEN_ROOT_CAUSE_CACHE
        + " WHERE ProjectId = '"
        + escape(projectId)
        + "'"
        + " AND screen_name = '"
        + escape(screenName)
        + "'"
        + " AND window_start_date = toDate('"
        + escape(windowStartDateIso)
        + "')"
        + " AND window_end_date = toDate('"
        + escape(windowEndDateIso)
        + "')";
  }

  public static String buildInsertQuery(
      final String projectId,
      final String screenName,
      final String windowStartDateIso,
      final String windowEndDateIso,
      final Instant windowStartInclusiveUtc,
      final Instant windowEndExclusiveUtc,
      final String resultJson,
      final LocalDateTime cachedAt) {
    DateTimeFormatter datetimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    String cachedAtStr = cachedAt.format(datetimeFmt);
    return INSERT_INTO_SCREEN_ROOT_CAUSE_CACHE
        + "("
        + "'"
        + escape(projectId)
        + "',"
        + "'"
        + escape(screenName)
        + "',"
        + "toDate('"
        + escape(windowStartDateIso)
        + "'),"
        + "toDate('"
        + escape(windowEndDateIso)
        + "'),"
        + toDateTime64Literal(windowStartInclusiveUtc)
        + ","
        + toDateTime64Literal(windowEndExclusiveUtc)
        + ","
        + "'"
        + escapeJson(resultJson)
        + "',"
        + "toDateTime64('"
        + escape(cachedAtStr)
        + "', 3, 'UTC')"
        + ")";
  }

  private static String toDateTime64Literal(Instant instant) {
    String formatted =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC)
            .format(instant);
    return "toDateTime64('" + escape(formatted) + "', 3, 'UTC')";
  }

  private static String escape(final String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }

  private static String escapeJson(final String s) {
    if (s == null) {
      return "{}";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
