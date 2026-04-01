package org.dreamhorizon.pulseserver.dao.journeyresults;

/** SQL for {@code otel.journey_results} (Spark → ClickHouse). */
public final class JourneyResultsQueries {

  private JourneyResultsQueries() {}

  /**
   * Latest run for a journey and direction (START | END). Rows ordered for stable graph build.
   *
   * <p>Physical columns are PascalCase per {@code clickhouse-journey-results-schema.sql}. Aliases
   * match {@link org.dreamhorizon.pulseserver.dao.journeyresults.models.JourneyResultRow}.
   */
  public static String buildLatestResultsSql(String projectId, long journeyId, String direction) {
    String pid = escapeChStringLiteral(projectId);
    String jid = Long.toString(journeyId);
    String dir = escapeChStringLiteral(direction);
    return "SELECT Direction AS direction, PosFrom AS posFrom, EventFrom AS eventFrom, PosTo AS posTo, "
        + "EventTo AS eventTo, UserCount AS userCount "
        + "FROM otel.journey_results WHERE JourneyId = "
        + jid
        + " AND ProjectId = '"
        + pid
        + "' AND Direction = '"
        + dir
        + "' AND RunTime = (SELECT max(RunTime) FROM otel.journey_results WHERE JourneyId = "
        + jid
        + " AND ProjectId = '"
        + pid
        + "' AND Direction = '"
        + dir
        + "') ORDER BY PosFrom, PosTo, EventFrom, EventTo";
  }

  static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
