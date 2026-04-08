package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

public class Queries {
  public static final String GET_SUGGESTIONS_BY_PROJECT =
      "SELECT id, project_id, pattern_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE project_id = ? AND status = 'PENDING' "
          + "ORDER BY session_pct DESC, cv ASC";

  public static final String GET_SUGGESTION_BY_ID =
      "SELECT id, project_id, pattern_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE id = ? AND project_id = ?";

  public static final String UPDATE_STATUS =
      "UPDATE suggested_interaction "
          + "SET status = ?, dismissed_by = ?, dismissed_at = NOW() "
          + "WHERE id = ? AND project_id = ?";
}
