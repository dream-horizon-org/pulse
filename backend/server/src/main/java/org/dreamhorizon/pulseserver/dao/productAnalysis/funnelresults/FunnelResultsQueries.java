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
   * <p>Physical columns are PascalCase per {@code funnel-results.sql}. Aliases
   * match {@link FunnelResultRow}.
   */
  public static String buildLatestResultsSql(String projectId, long funnelId) {
    String pid = escapeChStringLiteral(projectId);
    String fid = Long.toString(funnelId);
    return "SELECT StepIndex AS stepIndex, StepName AS stepName, "
      + "UserCount AS userCount, ConversionPct AS conversionPct, "
      + "MedianStepSeconds AS medianStepSeconds, RunTime AS runTime "
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

  /**
   * Overall conversion rate and trend for multiple funnels in one query.
   * <p>For each funnel returns:
   * <ul>
   *   <li>{@code funnelId} — the funnel id</li>
   *   <li>{@code conversionPct} — last step conversion % of the <b>latest</b> run</li>
   *   <li>{@code conversionTrend} — difference in conversion % between the latest and
   *       previous run (percentage points); 0 when only one run exists</li>
   * </ul>
   */
  public static String buildBulkOverallConversionRates(String projectId, java.util.List<Long> funnelIds) {
    String pid = escapeChStringLiteral(projectId);
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i < funnelIds.size(); i++) {
      if (i > 0) ids.append(',');
      ids.append(funnelIds.get(i));
    }
    // ranked: assign a dense rank per run (by RunTime DESC) and pick the last step per run
    // latest: run_rank = 1  →  current conversion
    // previous: run_rank = 2  →  prior conversion (for trend)
    return "SELECT "
      + "l.funnelId AS funnelId, "
      + "l.conversionPct AS conversionPct, "
      + "round(l.conversionPct - coalesce(p.conversionPct, l.conversionPct), 1) AS conversionTrend "
      + "FROM ("
      + "SELECT funnelId, conversionPct FROM ("
      + "SELECT FunnelId AS funnelId, ConversionPct AS conversionPct, "
      + "dense_rank() OVER (PARTITION BY FunnelId ORDER BY RunTime DESC) AS run_rank, "
      + "row_number() OVER (PARTITION BY FunnelId, RunTime ORDER BY StepIndex DESC) AS step_rank "
      + "FROM otel.funnel_results "
      + "WHERE ProjectId = '" + pid + "' AND FunnelId IN (" + ids + ")"
      + ") WHERE run_rank = 1 AND step_rank = 1"
      + ") l LEFT JOIN ("
      + "SELECT funnelId, conversionPct FROM ("
      + "SELECT FunnelId AS funnelId, ConversionPct AS conversionPct, "
      + "dense_rank() OVER (PARTITION BY FunnelId ORDER BY RunTime DESC) AS run_rank, "
      + "row_number() OVER (PARTITION BY FunnelId, RunTime ORDER BY StepIndex DESC) AS step_rank "
      + "FROM otel.funnel_results "
      + "WHERE ProjectId = '" + pid + "' AND FunnelId IN (" + ids + ")"
      + ") WHERE run_rank = 2 AND step_rank = 1"
      + ") p ON l.funnelId = p.funnelId";
  }

  public static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
