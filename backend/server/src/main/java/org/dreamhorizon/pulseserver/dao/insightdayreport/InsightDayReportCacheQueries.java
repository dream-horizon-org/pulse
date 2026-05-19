package org.dreamhorizon.pulseserver.dao.insightdayreport;

public final class InsightDayReportCacheQueries {

  private InsightDayReportCacheQueries() {}

  /**
   * Fetch existing per-day AI summaries for a given (project, type, entity) across a date IN list.
   * The IN clause placeholder is injected at runtime via {@code String.format}.
   */
  public static final String SELECT_FOR_DATES =
      "SELECT snapshot_date, day_body FROM insight_day_report"
          + " WHERE project_id = ? AND insight_type = ? AND entity_key = ?"
          + " AND snapshot_date IN (%s)";

  public static final String UPSERT =
      "INSERT INTO insight_day_report"
          + " (project_id, insight_type, entity_key, snapshot_date, day_body, cached_at)"
          + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))"
          + " ON DUPLICATE KEY UPDATE day_body = VALUES(day_body),"
          + " cached_at = VALUES(cached_at)";
}
