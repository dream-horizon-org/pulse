package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** SQL for otel.screen_root_cause_cache — same layout as {@code otel.root_cause_cache} but {@code screen_name}. */
public final class ScreenRootCauseCacheQueries {

  private ScreenRootCauseCacheQueries() {}

  public static final String SELECT_FROM_SCREEN_ROOT_CAUSE_CACHE =
      "SELECT ProjectId, screen_name, date, window_end_utc, mode, baseline, segments, cached_at"
          + " FROM otel.screen_root_cause_cache";

  public static final String INSERT_INTO_SCREEN_ROOT_CAUSE_CACHE =
      "INSERT INTO otel.screen_root_cause_cache (ProjectId, screen_name, date, window_end_utc, mode, baseline, "
          + "segments, cached_at) VALUES ";

  /**
   * Cache key matches interaction RCA: {@code (ProjectId, screen_name, date)} with {@code date} the anchor
   * UTC calendar day (same as {@link org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheQueries}).
   */
  public static String buildSelectByKeyQuery(
      final String projectId, final String screenName, final String dateIso) {
    return SELECT_FROM_SCREEN_ROOT_CAUSE_CACHE
        + " WHERE ProjectId = '"
        + escape(projectId)
        + "'"
        + " AND screen_name = '"
        + escape(screenName)
        + "'"
        + " AND date = '"
        + escape(dateIso)
        + "'";
  }

  public static String buildInsertQuery(
      final String projectId,
      final String screenName,
      final String dateIso,
      final Instant windowEndExclusiveUtc,
      final String mode,
      final String baselineJson,
      final String segmentsJson,
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
        + "'"
        + escape(dateIso)
        + "',"
        + toDateTime64Literal(windowEndExclusiveUtc)
        + ","
        + "'"
        + escape(mode)
        + "',"
        + "'"
        + escapeJson(baselineJson)
        + "',"
        + "'"
        + escapeJson(segmentsJson)
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
