package org.dreamhorizon.pulseserver.dao.rcareport;

/** SQL for pulse_db.screen_rca_narrative_cache (screen + UTC calendar window + payload fingerprint). */
public final class ScreenRcaNarrativeCacheQueries {

  private ScreenRcaNarrativeCacheQueries() {}

  /** Parameters: project_id, screen_name, window_start_date, window_end_date, payload_fingerprint. */
  public static final String GET_BY_KEY =
      "SELECT report_body, cached_at FROM screen_rca_narrative_cache"
          + " WHERE project_id = ? AND screen_name = ? AND window_start_date = ? AND window_end_date = ?"
          + " AND payload_fingerprint = ?";

  /** Insert or replace report for the composite key. */
  public static final String UPSERT =
      "INSERT INTO screen_rca_narrative_cache"
          + " (project_id, screen_name, window_start_date, window_end_date, payload_fingerprint,"
          + " report_body)"
          + " VALUES (?, ?, ?, ?, ?, ?)"
          + " ON DUPLICATE KEY UPDATE report_body = VALUES(report_body),"
          + " cached_at = CURRENT_TIMESTAMP(6)";
}
