package org.dreamhorizon.pulseserver.dao.insightreport;

public final class InsightReportCacheQueries {

  private InsightReportCacheQueries() {}

  public static final String GET_BY_KEY =
      "SELECT report_body, cached_at FROM insight_report"
          + " WHERE project_id = ? AND insight_type = ? AND entity_key = ?"
          + " AND execution_mode = ?"
          + " AND COALESCE(start_date, '1970-01-01') = COALESCE(?, '1970-01-01')"
          + " AND COALESCE(end_date, '1970-01-01') = COALESCE(?, '1970-01-01')";

  public static final String UPSERT =
      "INSERT INTO insight_report"
          + " (project_id, insight_type, entity_key, execution_mode, start_date, end_date,"
          + " report_body, cached_at)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))"
          + " ON DUPLICATE KEY UPDATE report_body = VALUES(report_body),"
          + " cached_at = VALUES(cached_at)";
}
