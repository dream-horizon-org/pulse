package org.dreamhorizon.pulseserver.dao.rcareport;

/** SQL for pulse_db.rca_report_cache (MySQL). */
public final class RcaReportCacheQueries {

  private RcaReportCacheQueries() {
  }

  /**
   * Get report body for key. Parameters: project_id, rca_type, entity_key, date.
   */
  public static final String GET_BY_KEY =
      "SELECT report_body, cached_at FROM rca_report_cache"
          + " WHERE project_id = ? AND rca_type = ? AND entity_key = ? AND date = ?";

  /** Insert or replace report for (project_id, rca_type, entity_key, date). */
  public static final String UPSERT =
      "INSERT INTO rca_report_cache (project_id, rca_type, entity_key, date, report_body, cached_at)"
          + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))"
          + " ON DUPLICATE KEY UPDATE report_body = VALUES(report_body), cached_at = VALUES(cached_at)";
}
