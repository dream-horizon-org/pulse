package org.dreamhorizon.pulseserver.dao.journeyresults;

/** SQL for {@code otel.journey_results} (Spark → ClickHouse). */
public final class JourneyResultsQueries {

  private JourneyResultsQueries() {}

  /**
   * Latest run for a journey and direction (START | END). Rows ordered for stable graph build.
   *
   * <p>Aliases match {@link org.dreamhorizon.pulseserver.dao.journeyresults.models.JourneyResultRow}.
   */
  public static String buildLatestResultsSql(String projectId, long journeyId, String direction) {
    String pid = escapeChStringLiteral(projectId);
    String jid = Long.toString(journeyId);
    String dir = escapeChStringLiteral(direction);
    return "SELECT direction, pos_from AS posFrom, event_from AS eventFrom, pos_to AS posTo, "
        + "event_to AS eventTo, user_count AS userCount "
        + "FROM otel.journey_results WHERE journey_id = '"
        + jid
        + "' AND project_id = '"
        + pid
        + "' AND direction = '"
        + dir
        + "' AND run_time = (SELECT max(run_time) FROM otel.journey_results WHERE journey_id = '"
        + jid
        + "' AND project_id = '"
        + pid
        + "' AND direction = '"
        + dir
        + "') ORDER BY pos_from, pos_to, event_from, event_to";
  }

  static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
