package org.dreamhorizon.pulseserver.dao.funnelresults;

/** Parameterized SQL for {@code otel.funnel_results} (Spark → ClickHouse). */
public final class FunnelResultsQueries {

  private FunnelResultsQueries() {}

  /**
   * Latest run for a funnel: all steps ordered by {@code step_index}.
   *
   * <p>Aliases match {@link org.dreamhorizon.pulseserver.dao.funnelresults.models.FunnelResultRow}
   * for Jackson deserialization from the generic ClickHouse client.
   */
  public static String buildLatestResultsSql(String projectId, long funnelId) {
    String pid = escapeChStringLiteral(projectId);
    String fid = Long.toString(funnelId);
    return "SELECT step_index AS stepIndex, step_name AS stepName, "
        + "user_count AS userCount, conversion_pct AS conversionPct "
        + "FROM otel.funnel_results WHERE funnel_id = '"
        + fid
        + "' AND project_id = '"
        + pid
        + "' AND run_time = (SELECT max(run_time) FROM otel.funnel_results WHERE funnel_id = '"
        + fid
        + "' AND project_id = '"
        + pid
        + "') ORDER BY step_index ASC";
  }

  static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
