package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

public final class Queries {

  private Queries() {}

  public static final String GET_SUGGESTIONS_BY_PROJECT =
      "SELECT id, project_id, events_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE project_id = ? AND status = 'PENDING' "
          + "ORDER BY session_pct DESC, cv ASC";

  public static final String GET_SUGGESTION_BY_ID =
      "SELECT id, project_id, events_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE id = ? AND project_id = ?";

  public static final String UPDATE_STATUS =
      "UPDATE suggested_interaction "
          + "SET status = ?, decided_by = ?, decided_at = NOW() "
          + "WHERE id = ? AND project_id = ?";

  public static final String GET_SUGGESTIONS_CATALOG_BY_PROJECT =
      "SELECT id, project_id, events_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE project_id = ? "
          + "ORDER BY created_at DESC, session_pct DESC, cv ASC";

  public static final String GET_SUGGESTIONS_CATALOG_BY_PROJECT_AND_STATUS =
      "SELECT id, project_id, events_json, total_occurrences, unique_sessions, "
          + "session_pct, mean_span_s, median_span_s, p95_span_s, cv, edges_json, "
          + "status, created_at "
          + "FROM suggested_interaction "
          + "WHERE project_id = ? AND status = ? "
          + "ORDER BY created_at DESC, session_pct DESC, cv ASC";

  public static final String DELETE_PENDING_BY_PROJECT =
      "DELETE FROM suggested_interaction WHERE project_id = ? AND status = 'PENDING'";

  public static final String INSERT_SUGGESTION =
      "INSERT INTO suggested_interaction ("
          + "project_id, events_json, total_occurrences, unique_sessions, session_pct, "
          + "mean_span_s, median_span_s, p95_span_s, cv, edges_json, status"
          + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')";
}
