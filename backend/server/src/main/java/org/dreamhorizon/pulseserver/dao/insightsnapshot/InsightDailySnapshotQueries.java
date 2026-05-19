package org.dreamhorizon.pulseserver.dao.insightsnapshot;

public final class InsightDailySnapshotQueries {

  private InsightDailySnapshotQueries() {}

  /**
   * ClickHouse queries use literal strings (not R2DBC binds). Format with
   * {@link String#format(String, Object...)}: projectId, insightType, entityKey, dateInClause.
   */
  public static final String SELECT_EXISTING_DATES =
      "SELECT SnapshotDate FROM otel.insight_daily_snapshots"
          + " WHERE ProjectId = '%s' AND InsightType = '%s' AND EntityKey = '%s'"
          + " AND SnapshotDate IN (%s)";

  public static final String SELECT_SNAPSHOTS_FOR_DATES =
      "SELECT SnapshotDate, ComputedData FROM otel.insight_daily_snapshots"
          + " WHERE ProjectId = '%s' AND InsightType = '%s' AND EntityKey = '%s'"
          + " AND SnapshotDate IN (%s)"
          + " ORDER BY SnapshotDate ASC";

}
