package org.dreamhorizon.pulseserver.dao.rootcause;

/** ClickHouse SQL for root_cause_cache (otel.root_cause_cache). */
public final class RootCauseCacheQueries {

  private RootCauseCacheQueries() {}

  /** SELECT with FINAL to get latest row per key (ReplacingMergeTree). */
  public static final String SELECT_BY_KEY =
      "SELECT tenant_id, project_id, interaction_name, date, mode, baseline, segments, cached_at "
          + "FROM otel.root_cause_cache FINAL "
          + "WHERE tenant_id = '%s' AND project_id = '%s' AND interaction_name = '%s' AND date = '%s' "
          + "LIMIT 1";

  /** INSERT one row. Values must be escaped (baseline, segments as JSON strings). */
  public static final String INSERT =
      "INSERT INTO otel.root_cause_cache (tenant_id, project_id, interaction_name, date, mode, baseline, segments, cached_at) "
          + "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', now64(3))";
}
