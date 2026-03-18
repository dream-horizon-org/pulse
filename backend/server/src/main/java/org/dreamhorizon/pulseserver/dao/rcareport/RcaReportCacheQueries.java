package org.dreamhorizon.pulseserver.dao.rcareport;

/** SQL for pulse_db.rca_report_cache (MySQL). TTL 24h enforced in SELECT. */
public final class RcaReportCacheQueries {

  private static final int CACHE_TTL_HOURS = 24;

  private RcaReportCacheQueries() {}

  /** Get report body if row exists and cached_at is within TTL. */
  public static final String GET_VALID =
      "SELECT report_body FROM rca_report_cache"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?"
          + " AND cached_at >= NOW() - INTERVAL " + CACHE_TTL_HOURS + " HOUR";

  /** Insert or replace report for (project_id, interaction_name, date). */
  public static final String UPSERT =
      "INSERT INTO rca_report_cache (project_id, interaction_name, date, report_body, cached_at)"
          + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))"
          + " ON DUPLICATE KEY UPDATE report_body = VALUES(report_body), cached_at = VALUES(cached_at)";
}
