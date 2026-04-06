package org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;

/**
 * Parameterized SQL for {@code otel.funnel_results} (Spark → ClickHouse).
 */
public final class FunnelResultsQueries {

  private FunnelResultsQueries() {
  }

  /**
   * Latest run for a funnel: all steps ordered by {@code StepIndex}.
   *
   * <p>Physical columns are PascalCase per {@code clickhouse-funnel-results-schema.sql}. Aliases
   * match {@link FunnelResultRow}.
   */
  public static String buildLatestResultsSql(String projectId, long funnelId) {
    String pid = escapeChStringLiteral(projectId);
    String fid = Long.toString(funnelId);
    return "SELECT StepIndex AS stepIndex, StepName AS stepName, "
      + "UserCount AS userCount, ConversionPct AS conversionPct "
      + "FROM otel.funnel_results WHERE FunnelId = "
      + fid
      + " AND ProjectId = '"
      + pid
      + "' AND RunTime = (SELECT max(RunTime) FROM otel.funnel_results WHERE FunnelId = "
      + fid
      + " AND ProjectId = '"
      + pid
      + "') ORDER BY StepIndex ASC";
  }

  static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
