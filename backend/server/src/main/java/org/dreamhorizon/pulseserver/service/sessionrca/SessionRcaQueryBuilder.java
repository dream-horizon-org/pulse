package org.dreamhorizon.pulseserver.service.sessionrca;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;

/**
 * ClickHouse queries for Session RCA over {@code otel.session_summary}.
 * Reuses {@link RootCauseQueryBuilder.BindAccumulator} and {@link RootCauseQueryBuilder.Window}.
 * Bind param prefix: {@code srca_p}.
 */
public final class SessionRcaQueryBuilder {

  static final String SESSION_SUMMARY_TABLE = "otel.session_summary";

  private static final Set<String> VALID_DIMENSION_COLUMNS = Set.of(
      "platform", "osVersion", "appVersion", "startType",
      "deviceModel", "networkProvider", "geoRegion");

  private SessionRcaQueryBuilder() {}

  /**
   * Baseline: µ, σ, and total project-wide volume. Critical threshold is computed by the caller.
   * Returns one row.
   */
  public static RootCauseQuerySpec buildBaselineQuery(
      String projectId, Instant startInclusive, Instant endExclusive) {
    BindAccumulator acc = new BindAccumulator();
    String where = baseWhereSql(acc, projectId, startInclusive, endExclusive);
    String sql = "SELECT"
        + " count() AS " + SessionRcaMetricsRegistry.VOLUME
        + ", countIf(apdexCount > 0) AS " + SessionRcaMetricsRegistry.VOLUME_WITH_APDEX
        + ", " + SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN_EXPR + " AS " + SessionRcaMetricsRegistry.QUALITY_SCORE
        + ", " + SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN_EXPR + " AS " + SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN
        + ", " + SessionRcaMetricsRegistry.QUALITY_SCORE_STD_EXPR + " AS " + SessionRcaMetricsRegistry.QUALITY_SCORE_STD
        + " FROM " + SESSION_SUMMARY_TABLE
        + " WHERE " + where;
    return acc.toSpec(sql);
  }

  /**
   * p20 and p80 of session duration (ms). Returns one row with columns {@code p20} and {@code p80}.
   * Run before SessionLength dimension queries.
   */
  public static RootCauseQuerySpec buildSessionLengthPercentilesQuery(
      String projectId, Instant startInclusive, Instant endExclusive) {
    BindAccumulator acc = new BindAccumulator();
    String where = baseWhereSql(acc, projectId, startInclusive, endExclusive);
    String durationExpr =
        "toInt64(toUnixTimestamp64Milli(endTime) - toUnixTimestamp64Milli(startTime))";
    String sql = "SELECT"
        + " quantile(0.20)(" + durationExpr + ") AS p20"
        + ", quantile(0.80)(" + durationExpr + ") AS p80"
        + " FROM " + SESSION_SUMMARY_TABLE
        + " WHERE " + where;
    return acc.toSpec(sql);
  }

  /**
   * Dimension selection query: returns {@code low_quality_count} per value of {@code dimensionColumn}.
   * Used for both hierarchical trigger (pickClosestToTotal) and flat ranking (MAX low_quality_count).
   *
   * @param dimensionFilters optional cumulative hierarchy filters (AND each)
   * @param criticalThreshold the project-level µ − 2σ threshold
   */
  public static RootCauseQuerySpec buildLowQualityCountByDimensionQuery(
      String projectId,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    validateDimension(dimensionColumn);
    BindAccumulator acc = new BindAccumulator();
    String thresholdParam = acc.nextName();
    acc.add(thresholdParam, criticalThreshold);
    String where = appendDimensionFilters(
        baseWhereSql(acc, projectId, startInclusive, endExclusive), acc, dimensionFilters, p20Ms, p80Ms);
    String dimExpr = dimensionExpression(dimensionColumn, acc, p20Ms, p80Ms);
    String sql = "SELECT"
        + " " + dimExpr + " AS " + dimensionColumn
        + ", " + SessionRcaMetricsRegistry.lowQualityCountExpr(thresholdParam)
        + " AS " + SessionRcaMetricsRegistry.LOW_QUALITY_COUNT
        + " FROM " + SESSION_SUMMARY_TABLE
        + " WHERE " + where
        + " GROUP BY " + dimensionColumn;
    return acc.toSpec(sql);
  }

  /**
   * Full segment metrics query: volume + quality_score per segment (GROUP BY all given dimensions).
   */
  public static RootCauseQuerySpec buildSegmentQuery(
      String projectId,
      Instant startInclusive,
      Instant endExclusive,
      List<String> dimensionColumns,
      Map<String, String> dimensionFilters,
      long p20Ms,
      long p80Ms) {
    if (dimensionColumns == null || dimensionColumns.isEmpty()) {
      throw new IllegalArgumentException("dimensionColumns must be non-empty for segment query");
    }
    for (String dim : dimensionColumns) {
      validateDimension(dim);
    }
    BindAccumulator acc = new BindAccumulator();
    String where = appendDimensionFilters(
        baseWhereSql(acc, projectId, startInclusive, endExclusive), acc, dimensionFilters, p20Ms, p80Ms);
    StringBuilder select = new StringBuilder();
    for (String dim : dimensionColumns) {
      if (select.length() > 0) {
        select.append(", ");
      }
      select.append(dimensionExpression(dim, acc, p20Ms, p80Ms)).append(" AS ").append(dim);
    }
    select.append(", count() AS ").append(SessionRcaMetricsRegistry.VOLUME);
    select.append(", ").append(SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN_EXPR)
        .append(" AS ").append(SessionRcaMetricsRegistry.QUALITY_SCORE);
    String groupBy = dimensionColumns.stream().collect(Collectors.joining(", "));
    String sql = "SELECT " + select
        + " FROM " + SESSION_SUMMARY_TABLE
        + " WHERE " + where
        + " GROUP BY " + groupBy;
    return acc.toSpec(sql);
  }

  /**
   * Returns up to {@code limit} session IDs from {@code session_summary} that match the
   * segment's dimension filters and have quality_score below the critical threshold.
   * Ordered by quality_score ASC (worst sessions first) so the most degraded examples surface.
   */
  public static RootCauseQuerySpec buildExampleSessionsQuery(
      String projectId,
      Instant startInclusive,
      Instant endExclusive,
      Map<String, String> dimensionFilters,
      double criticalThreshold,
      int limit,
      long p20Ms,
      long p80Ms) {
    BindAccumulator acc = new BindAccumulator();
    String thresholdParam = acc.nextName();
    acc.add(thresholdParam, criticalThreshold);
    String where = appendDimensionFilters(
        baseWhereSql(acc, projectId, startInclusive, endExclusive), acc, dimensionFilters, p20Ms, p80Ms);
    String qualityExpr = "apdexSum / apdexCount";
    String sql = "SELECT sessionId"
        + " FROM " + SESSION_SUMMARY_TABLE
        + " WHERE " + where
        + " AND apdexCount > 0"
        + " AND (" + qualityExpr + ") < :" + thresholdParam
        + " ORDER BY (" + qualityExpr + ") ASC"
        + " LIMIT " + limit;
    return acc.toSpec(sql);
  }

  /**
   * WHERE clause for session_summary: project + time window.
   * Uses bind params prefixed {@code srca_p}.
   */
  static String baseWhereSql(
      BindAccumulator acc, String projectId, Instant startInclusive, Instant endExclusive) {
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    acc.add(p0, projectId == null ? "" : projectId);
    String startStr = startInclusive.atOffset(ZoneOffset.UTC)
        .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr = endExclusive.atOffset(ZoneOffset.UTC)
        .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p1, startStr);
    acc.add(p2, endStr);
    return "ProjectId = :" + p0
        + " AND startTime >= toDateTime64(:" + p1 + ", 9, 'UTC')"
        + " AND startTime < toDateTime64(:" + p2 + ", 9, 'UTC')";
  }

  private static String appendDimensionFilters(
      String baseWhere, BindAccumulator acc, Map<String, String> filters, long p20Ms, long p80Ms) {
    if (filters == null || filters.isEmpty()) {
      return baseWhere;
    }
    StringBuilder sb = new StringBuilder(baseWhere);
    for (Map.Entry<String, String> e : filters.entrySet()) {
      String pn = acc.nextName();
      acc.add(pn, e.getValue() == null ? "" : e.getValue());
      String lhs = "(" + dimensionExpression(e.getKey(), acc, p20Ms, p80Ms) + ")";
      sb.append(" AND ").append(lhs).append(" = :").append(pn);
    }
    return sb.toString();
  }

  /**
   * Returns the ClickHouse expression for the dimension column. For SessionLength, the p20/p80
   * values must already be bound (they are literals, not bind params, to keep GROUP BY simple).
   */
  static String dimensionExpression(String dim, BindAccumulator acc, long p20Ms, long p80Ms) {
    if ("SessionLength".equals(dim)) {
      String durExpr =
          "toInt64(toUnixTimestamp64Milli(endTime) - toUnixTimestamp64Milli(startTime))";
      return "multiIf(" + durExpr + " < " + p20Ms + ", 'Short',"
          + durExpr + " > " + p80Ms + ", 'Long', 'Typical')";
    }
    return dimensionExpressionRaw(dim);
  }

  private static String dimensionExpressionRaw(String dim) {
    return switch (dim) {
      case "platform" -> "platform";
      case "osVersion" -> "osVersion";
      case "appVersion" -> "appVersion";
      case "startType" -> "startType";
      case "deviceModel" -> "deviceModel";
      case "networkProvider" -> "networkProvider";
      case "geoRegion" -> "geoRegion";
      case "SessionLength" -> throw new IllegalStateException(
          "SessionLength requires p20/p80 — use dimensionExpression(dim, acc, p20, p80)");
      default -> throw new IllegalArgumentException("Unknown Session RCA dimension: " + dim);
    };
  }

  private static void validateDimension(String dim) {
    if (!VALID_DIMENSION_COLUMNS.contains(dim) && !"SessionLength".equals(dim)) {
      throw new IllegalArgumentException("Invalid Session RCA dimension: " + dim);
    }
  }

  /**
   * Bind accumulator for session RCA queries; prefix {@code srca_p} to avoid clashes if specs
   * are ever composed.
   */
  public static final class BindAccumulator {
    private int nextSeq;
    private final java.util.List<String> bindNames = new java.util.ArrayList<>();
    private final java.util.List<Object> bindValues = new java.util.ArrayList<>();

    public String nextName() {
      String name = "srca_p" + nextSeq;
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
}
