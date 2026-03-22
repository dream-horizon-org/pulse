package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** SQL for otel.root_cause_cache (ReplacingMergeTree). */
public final class RootCauseCacheQueries {

  private RootCauseCacheQueries() {
  }

  /**
   * SELECT list and source for cache reads; WHERE clause is appended by
   * {@link #buildSelectByKeyQuery(String, String, String)}.
   */
  public static final String SELECT_FROM_ROOT_CAUSE_CACHE_FINAL =
      "SELECT project_id, interaction_name, date, mode, baseline, segments, cached_at"
          + " FROM otel.root_cause_cache FINAL";

  /**
   * INSERT target columns; VALUES tuple is built by
   * {@link #buildInsertQuery(String, String, String, String, String, String, LocalDateTime)}.
   */
  public static final String INSERT_INTO_ROOT_CAUSE_CACHE =
      "INSERT INTO otel.root_cause_cache (project_id, interaction_name, date, mode, baseline, "
          + "segments, cached_at) VALUES ";

  /**
   * Builds a SELECT for one cache key (escaped string literals for ClickHouse HTTP query).
   *
   * @param projectId project id
   * @param interactionName interaction name
   * @param dateIso cache date as {@code yyyy-MM-dd}
   * @return full SQL
   */
  public static String buildSelectByKeyQuery(
      final String projectId,
      final String interactionName,
      final String dateIso) {
    return SELECT_FROM_ROOT_CAUSE_CACHE_FINAL
        + " WHERE project_id = '" + escape(projectId) + "'"
        + " AND interaction_name = '" + escape(interactionName) + "'"
        + " AND date = '" + escape(dateIso) + "'";
  }

  /**
   * Builds an INSERT for one cache row.
   *
   * @param projectId project id
   * @param interactionName interaction name
   * @param dateIso cache date as {@code yyyy-MM-dd}
   * @param mode analysis mode
   * @param baselineJson baseline JSON
   * @param segmentsJson segments JSON
   * @param cachedAt row timestamp (UTC)
   * @return full SQL
   */
  public static String buildInsertQuery(
      final String projectId,
      final String interactionName,
      final String dateIso,
      final String mode,
      final String baselineJson,
      final String segmentsJson,
      final LocalDateTime cachedAt) {
    DateTimeFormatter datetimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    String cachedAtStr = cachedAt.format(datetimeFmt);
    return INSERT_INTO_ROOT_CAUSE_CACHE
        + "("
        + "'" + escape(projectId) + "',"
        + "'" + escape(interactionName) + "',"
        + "'" + dateIso + "',"
        + "'" + escape(mode) + "',"
        + "'" + escapeJson(baselineJson) + "',"
        + "'" + escapeJson(segmentsJson) + "',"
        + "toDateTime64('" + escape(cachedAtStr) + "', 3, 'UTC')"
        + ")";
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
