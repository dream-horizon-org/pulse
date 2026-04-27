package org.dreamhorizon.pulseserver.service.rootcause;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
   * Values are passed via {@link RootCauseQuerySpec} named binds ({@code :rca_p0}, …) for ClickHouse R2DBC.
   */
  public static String baseWhereSql(
      BindAccumulator acc,
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive) {
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, emptyIfNull(projectId));
    acc.add(p1, emptyIfNull(interactionName));
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    return "ProjectId = :"
        + p0
        + " AND PulseType = '"
        + InteractionTelemetryConstants.INTERACTION_PULSE_TYPE
        + "'"
        + " AND SpanName = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  /**
   * Non-empty {@code screen.name} values for spans matching {@code pulse.interaction.name} (session
   * listing semantics), same time window as RCA heatmap filters. One row with column {@code screens}:
   * distinct names ordered by descending span count, then ascending name for ties.
   */
  public static RootCauseQuerySpec buildDistinctScreensForInteractionQuery(
      String projectId, String interactionName, Window window) {
    return buildDistinctScreensForInteractionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive);
  }

  /**
   * @see #buildDistinctScreensForInteractionQuery(String, String, Window)
   */
  public static RootCauseQuerySpec buildDistinctScreensForInteractionQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive) {
    BindAccumulator acc = new BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, emptyIfNull(projectId));
    acc.add(p1, emptyIfNull(interactionName));
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    String where =
        "ProjectId = :"
            + p0
            + " AND PulseType = '"
            + InteractionTelemetryConstants.INTERACTION_PULSE_TYPE
            + "'"
            + " AND nullIf(trimBoth(SpanAttributes['pulse.interaction.name']), '') = :"
            + p1
            + " AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC')";
    String spanRows =
        "SELECT nullIf(trimBoth(SpanAttributes['screen.name']), '') AS screen_name FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where;
    String sql =
        "SELECT arrayMap(x -> x.2, arraySort(x -> (x.1, x.2), groupArray(tuple(-toInt64(cnt), screen_name)))) AS screens FROM ("
            + "SELECT screen_name, count() AS cnt FROM ("
            + spanRows
            + ") AS interaction_spans WHERE screen_name != '' GROUP BY screen_name"
            + ") AS per_screen";
    return acc.toSpec(sql);
  }

  /**
   * Builds baseline query: no GROUP BY, returns one row with all metrics + problematic_count.
   */
  public static RootCauseQuerySpec buildBaselineQuery(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive) {
    BindAccumulator acc = new BindAccumulator();
    String select = buildSelectClauseWithProblematic();
    String where = baseWhereSql(acc, projectId, interactionName, startInclusive, endExclusive);
    String sql = "SELECT " + select + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
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
      Map<String, String> dimensionFilters) {
    if (dimensionColumns == null || dimensionColumns.isEmpty()) {
      throw new IllegalArgumentException("dimensionColumns must be non-empty for segment query");
    }
    BindAccumulator acc = new BindAccumulator();
    String select = buildSelectClauseWithProblematicAndGroupBy(dimensionColumns);
    String where =
        appendDimensionFilters(
            baseWhereSql(acc, projectId, interactionName, startInclusive, endExclusive),
            acc,
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
    return acc.toSpec(sql);
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
      Map<String, String> dimensionFilters) {
    BindAccumulator acc = new BindAccumulator();
    String select =
        dimensionColumn + ", " + RootCauseMetricsRegistry.getProblematicCountExpression() + " AS problematic_count";
    String where =
        appendDimensionFilters(
            baseWhereSql(acc, projectId, interactionName, startInclusive, endExclusive),
            acc,
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
    return acc.toSpec(sql);
  }

  private static String appendDimensionFilters(
      String baseWhereSql, BindAccumulator acc, Map<String, String> dimensionFilters) {
    if (dimensionFilters == null || dimensionFilters.isEmpty()) {
      return baseWhereSql;
    }
    StringBuilder sb = new StringBuilder(baseWhereSql);
    for (Map.Entry<String, String> e : dimensionFilters.entrySet()) {
      String pn = acc.nextName();
      acc.add(pn, emptyIfNull(e.getValue()));
      sb.append(" AND ").append(e.getKey()).append(" = :").append(pn);
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

  /** Collects {@code :rca_pN} names and values for {@link RootCauseQuerySpec}. */
  public static final class BindAccumulator {
    private int nextSeq;
    private final List<String> bindNames = new ArrayList<>();
    private final List<Object> bindValues = new ArrayList<>();

    public String nextName() {
      String name = "rca_p" + nextSeq;
      nextSeq += 1;
      return name;
    }

    public void add(String name, Object value) {
      bindNames.add(name);
      bindValues.add(value);
    }

    public RootCauseQuerySpec toSpec(String sql) {
      return new RootCauseQuerySpec(sql, bindNames, bindValues);
    }
  }

  /**
   * RCA window: {@code lookbackDays - 1} full UTC calendar days before {@code anchorDateUtc}, plus the
   * partial day from UTC midnight on {@code anchorDateUtc} up to {@code endExclusiveUtc} (exclusive bound
   * on span timestamps).
   */
  public static class Window {
    public final Instant startInclusive;
    public final Instant endExclusive;

    public Window(LocalDate anchorDateUtc, int lookbackDays, Instant endExclusiveUtc) {
      if (lookbackDays < 1) {
        throw new IllegalArgumentException("lookbackDays must be >= 1");
      }
      Instant startInclusiveUtc =
          anchorDateUtc.minusDays(lookbackDays - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (!endExclusiveUtc.isAfter(startInclusiveUtc)) {
        throw new IllegalArgumentException("endExclusiveUtc must be after startInclusive");
      }
      this.startInclusive = startInclusiveUtc;
      this.endExclusive = endExclusiveUtc;
    }

    /**
     * Exact {@code [startInclusive, endExclusive)} bounds (e.g. UI date range). Does not apply lookback days.
     */
    public static Window explicit(Instant startInclusive, Instant endExclusive) {
      Objects.requireNonNull(startInclusive, "startInclusive");
      Objects.requireNonNull(endExclusive, "endExclusive");
      if (!endExclusive.isAfter(startInclusive)) {
        throw new IllegalArgumentException("endExclusive must be after startInclusive");
      }
      return new Window(startInclusive, endExclusive);
    }

    private Window(Instant startInclusive, Instant endExclusive) {
      this.startInclusive = startInclusive;
      this.endExclusive = endExclusive;
    }
  }
}
