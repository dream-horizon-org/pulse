package org.dreamhorizon.pulseserver.dao.rcareport;

/**
 * SQL for pulse_db.rca_report_cache (MySQL). Cache TTL (hours) is the fourth bound parameter on
 * {@link #GET_VALID}.
 */
public final class RcaReportCacheQueries {

  private RcaReportCacheQueries() {
  }

  /**
   * Get report body if row exists and {@code cached_at} is within the TTL (hours).
   * Parameters: project_id, interaction_name, date, ttl_hours.
   */
  public static final String GET_VALID =
      "SELECT report_body, cached_at FROM rca_report_cache"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?"
          + " AND cached_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)";

  /** Insert or replace report for (project_id, interaction_name, date). */
  public static final String UPSERT =
      "INSERT INTO rca_report_cache (project_id, interaction_name, date, report_body, cached_at)"
          + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))"
          + " ON DUPLICATE KEY UPDATE report_body = VALUES(report_body), cached_at = VALUES(cached_at)";
}
