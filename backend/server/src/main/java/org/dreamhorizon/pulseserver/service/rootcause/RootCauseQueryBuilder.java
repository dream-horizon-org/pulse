package org.dreamhorizon.pulseserver.service.rootcause;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds ClickHouse SELECTs for Root Cause Analysis:
 * (1) Baseline: no GROUP BY, same WHERE (PulseType='interaction', SpanName=?, Timestamp in window, ProjectId=?).
 * (2) Segment: GROUP BY dimension(s), same WHERE plus dimension filters.
 * Includes problematic count (error OR poor) for segment selection.
 */
@Slf4j
public class RootCauseQueryBuilder {

  private static final String TABLE = "otel.otel_traces";
  private static final DateTimeFormatter CLICKHOUSE_DT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Builds the common WHERE clause for interaction traces in the time window.
   */
  public static String baseWhere(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive
  ) {
    String startStr = startInclusive.atOffset(ZoneOffset.UTC).format(CLICKHOUSE_DT);
    String endStr = endExclusive.atOffset(ZoneOffset.UTC).format(CLICKHOUSE_DT);
    return "ProjectId = '" + escape(projectId) + "'"
        + " AND PulseType = 'interaction'"
        + " AND SpanName = '" + escape(interactionName) + "'"
        + " AND Timestamp >= toDateTime64('" + startStr + "', 9, 'UTC')"
        + " AND Timestamp < toDateTime64('" + endStr + "', 9, 'UTC')";
  }

  /**
   * Builds baseline query: no GROUP BY, returns one row with all metrics + problematic_count.
   */
  public static String buildBaselineQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive
  ) {
    String select = buildSelectClauseWithProblematic();
    String where = baseWhere(projectId, interactionName, startInclusive, endExclusive);
    return "SELECT " + select + " FROM " + TABLE + " WHERE " + where;
  }

  /**
   * Builds segment query: GROUP BY given dimensions, optional dimension filters.
   * Returns one row per segment with dimension columns, all metrics, and problematic_count.
   *
   * @param dimensionColumns dimension names to GROUP BY (e.g. Platform, OsVersion)
   * @param dimensionFilters optional map dimension -> value to filter (AND each)
   */
  public static String buildSegmentQuery(
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
    String select = buildSelectClauseWithProblematicAndGroupBy(dimensionColumns);
    String where = baseWhere(projectId, interactionName, startInclusive, endExclusive);
    if (dimensionFilters != null && !dimensionFilters.isEmpty()) {
      for (Map.Entry<String, String> e : dimensionFilters.entrySet()) {
        where += " AND " + e.getKey() + " = '" + escape(e.getValue()) + "'";
      }
    }
    String groupBy = dimensionColumns.stream().collect(Collectors.joining(", "));
    return "SELECT " + select + " FROM " + TABLE + " WHERE " + where + " GROUP BY " + groupBy;
  }

  /**
   * Builds a query that only returns problematic_count (and optionally one dimension for values).
   * Used for first-dimension and add-dimension steps (segment selection).
   */
  public static String buildProblematicCountByDimensionQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters
  ) {
    String select = dimensionColumn + ", " + RootCauseMetricsRegistry.getProblematicCountExpression() + " AS problematic_count";
    String where = baseWhere(projectId, interactionName, startInclusive, endExclusive);
    if (dimensionFilters != null && !dimensionFilters.isEmpty()) {
      for (Map.Entry<String, String> e : dimensionFilters.entrySet()) {
        where += " AND " + e.getKey() + " = '" + escape(e.getValue()) + "'";
      }
    }
    return "SELECT " + select + " FROM " + TABLE + " WHERE " + where + " GROUP BY " + dimensionColumn;
  }

  private static String buildSelectClauseWithProblematic() {
    StringBuilder sb = new StringBuilder();
    Map<String, String> metrics = RootCauseMetricsRegistry.getMetricExpressions();
    for (Map.Entry<String, String> e : metrics.entrySet()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(e.getValue()).append(" AS ").append(e.getKey());
    }
    sb.append(", ").append(RootCauseMetricsRegistry.getProblematicCountExpression()).append(" AS problematic_count");
    return sb.toString();
  }

  private static String buildSelectClauseWithProblematicAndGroupBy(List<String> dimensionColumns) {
    StringBuilder sb = new StringBuilder();
    for (String d : dimensionColumns) {
      if (sb.length() > 0) sb.append(", ");
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

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
