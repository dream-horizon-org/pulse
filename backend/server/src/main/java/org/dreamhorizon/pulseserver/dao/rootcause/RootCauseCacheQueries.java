package org.dreamhorizon.pulseserver.dao.rootcause;

/** SQL for otel.root_cause_cache (ReplacingMergeTree). */
public final class RootCauseCacheQueries {

  private RootCauseCacheQueries() {}

  /** Read latest row by key; FINAL to apply ReplacingMergeTree. */
  public static final String SELECT_BY_KEY =
      "SELECT project_id, interaction_name, date, mode, baseline, segments, cached_at"
          + " FROM otel.root_cause_cache FINAL"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?";

  /** Insert one row; ReplacingMergeTree will keep latest by cached_at. */
  public static final String INSERT =
      "INSERT INTO otel.root_cause_cache (project_id, interaction_name, date, mode, baseline, segments, cached_at)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)";
}
