package org.dreamhorizon.pulseserver.service.rootcause;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.interaction.InteractionTelemetryConstants;

/**
 * Builds ClickHouse SELECTs for Root Cause Analysis:
 * (1) Baseline: no GROUP BY, same WHERE ({@link InteractionTelemetryConstants#INTERACTION_PULSE_TYPE},
 *     bound SpanName/Timestamp/ProjectId).
 * (2) Segment: GROUP BY dimension(s), same WHERE plus dimension filters (bound values).
 * Includes problematic count (error OR poor) for segment selection.
 */
public class RootCauseQueryBuilder {

  /**
   * Builds the common WHERE clause for interaction traces in the time window.
   * Values are passed via {@link RootCauseQuerySpec#bindParameters()} (positional {@code ?}).
   */
  public static String baseWhereSql(List<Object> outBinds, String projectId, String interactionName,
      Instant startInclusive, Instant endExclusive) {
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    outBinds.add(emptyIfNull(projectId));
    outBinds.add(emptyIfNull(interactionName));
    outBinds.add(startStr);
    outBinds.add(endStr);
    return "ProjectId = ?"
        + " AND PulseType = '" + InteractionTelemetryConstants.INTERACTION_PULSE_TYPE + "'"
        + " AND SpanName = ?"
        + " AND Timestamp >= toDateTime64(?, 9, 'UTC')"
        + " AND Timestamp < toDateTime64(?, 9, 'UTC')";
  }

  /**
   * Builds baseline query: no GROUP BY, returns one row with all metrics + problematic_count.
   */
  public static RootCauseQuerySpec buildBaselineQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive
  ) {
    List<Object> binds = new ArrayList<>();
    String select = buildSelectClauseWithProblematic();
    String where = baseWhereSql(binds, projectId, interactionName, startInclusive, endExclusive);
    String sql = "SELECT " + select + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return new RootCauseQuerySpec(sql, binds);
  }

  /**
   * Builds segment query: GROUP BY given dimensions, optional dimension filters.
   * Returns one row per segment with dimension columns, all metrics, and problematic_count.
   *
   * @param dimensionColumns dimension names to GROUP BY (e.g. Platform, OsVersion)
   * @param dimensionFilters optional map dimension -> value to filter (AND each)
   */
  public static RootCauseQuerySpec buildSegmentQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      List<String> dimensionColumns,
      Map<String, String> dimensionFilters
  ) {
    if (dimensionColumns == null || dimensionColumns.isEmpty()) {
      throw new IllegalArgumentException("dimensionColumns must be non-empty for segment query");
    }
    List<Object> binds = new ArrayList<>();
    String select = buildSelectClauseWithProblematicAndGroupBy(dimensionColumns);
    String where =
        appendDimensionFilters(
            baseWhereSql(binds, projectId, interactionName, startInclusive, endExclusive),
            binds,
            dimensionFilters);
    String groupBy = dimensionColumns.stream().collect(Collectors.joining(", "));
    String sql =
        "SELECT "
            + select
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + groupBy;
    return new RootCauseQuerySpec(sql, binds);
  }

  /**
   * Builds a query that only returns problematic_count (and optionally one dimension for values).
   * Used for first-dimension and add-dimension steps (segment selection).
   */
  public static RootCauseQuerySpec buildProblematicCountByDimensionQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters
  ) {
    List<Object> binds = new ArrayList<>();
    String select =
        dimensionColumn + ", " + RootCauseMetricsRegistry.getProblematicCountExpression() + " AS problematic_count";
    String where =
        appendDimensionFilters(
            baseWhereSql(binds, projectId, interactionName, startInclusive, endExclusive),
            binds,
            dimensionFilters);
    String sql =
        "SELECT "
            + select
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return new RootCauseQuerySpec(sql, binds);
  }

  private static String appendDimensionFilters(
      String baseWhereSql, List<Object> outBinds, Map<String, String> dimensionFilters) {
    if (dimensionFilters == null || dimensionFilters.isEmpty()) {
      return baseWhereSql;
    }
    StringBuilder sb = new StringBuilder(baseWhereSql);
    for (Map.Entry<String, String> e : dimensionFilters.entrySet()) {
      sb.append(" AND ").append(e.getKey()).append(" = ?");
      outBinds.add(emptyIfNull(e.getValue()));
    }
    return sb.toString();
  }

  private static String emptyIfNull(String s) {
    return s == null ? "" : s;
  }

  private static String buildSelectClauseWithProblematic() {
    StringBuilder sb = new StringBuilder();
    Map<String, String> metrics = RootCauseMetricsRegistry.getMetricExpressions();
    for (Map.Entry<String, String> e : metrics.entrySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(e.getValue()).append(" AS ").append(e.getKey());
    }
    sb.append(", ").append(RootCauseMetricsRegistry.getProblematicCountExpression()).append(" AS problematic_count");
    return sb.toString();
  }

  private static String buildSelectClauseWithProblematicAndGroupBy(List<String> dimensionColumns) {
    StringBuilder sb = new StringBuilder();
    for (String d : dimensionColumns) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(d);
    }
    Map<String, String> metrics = RootCauseMetricsRegistry.getMetricExpressions();
    for (Map.Entry<String, String> e : metrics.entrySet()) {
      sb.append(", ").append(e.getValue()).append(" AS ").append(e.getKey());
    }
    sb.append(", ").append(RootCauseMetricsRegistry.getProblematicCountExpression()).append(" AS problematic_count");
    return sb.toString();
  }

  /** Compute start (inclusive) and end (exclusive) for "last N days ending on date" (date = end of window, UTC). */
  public static class Window {
    public final Instant startInclusive;
    public final Instant endExclusive;

    public Window(LocalDate endDateUtc, int lookbackDays) {
      this.endExclusive = endDateUtc.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      this.startInclusive = endDateUtc.minusDays(lookbackDays).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
  }
}
