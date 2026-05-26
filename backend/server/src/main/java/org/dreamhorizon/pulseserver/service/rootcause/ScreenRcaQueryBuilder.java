package org.dreamhorizon.pulseserver.service.rootcause;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * ClickHouse queries for Screen-scoped RCA over {@code otel.otel_logs} (pulse type {@value
 * #APP_CLICK_PULSE_TYPE} rows).
 * Uses {@link RootCauseQueryBuilder.BindAccumulator} / {@link RootCauseQuerySpec} like interaction RCA.
 */
public final class ScreenRcaQueryBuilder {

  public static final String APP_CLICK_PULSE_TYPE = "app.click";

  /** Log rows in the cohort (tap events). */
  public static final String CLICK_VOLUME = "click_volume";

  public static final String TAP_COUNT = "tap_count";
  public static final String RAGE_COUNT = "rage_count";
  public static final String DEAD_COUNT = "dead_count";
  /** Segmentation driver: dead or rage clicks (mutually exclusive per click; equals dead_count + rage_count). */
  public static final String BAD_FRUSTRATION = "bad_frustration";

  /**
   * {@code bad_frustration / click_volume * 100} (0–100 scale). Not selected in ClickHouse — computed in
   * {@link ScreenRcaService} from {@link #BAD_FRUSTRATION} and {@link #CLICK_VOLUME} on baseline/segment rows.
   */
  public static final String BAD_FRUSTRATION_PERCENTAGE = "bad_frustration_percentage";

  /** Materialized {@code ClickType} / {@code Rage} on {@code otel.otel_logs} (see dev DDL). */
  private static final String TAP_COUNT_EXPR = "countIf(ClickType = 'good' AND NOT Rage)";

  private static final String RAGE_COUNT_EXPR = "countIf(Rage)";

  private static final String DEAD_COUNT_EXPR = "countIf(ClickType = 'dead')";

  private static final String BAD_FRUSTRATION_EXPR =
      "countIf(ClickType = 'dead' OR Rage)";

  private ScreenRcaQueryBuilder() {}

  /**
   * WHERE: project, time window, {@code PulseType = app.click}, non-empty trimmed {@code ScreenName}.
   *
   * <p>Requires materialized columns from {@code backend/db/dev/clickhouse/otel.otel_logs.sql}; apply
   * {@code backend/db/dev/clickhouse/otel.otel_logs_z_alter_screen_rca_columns.sql}
   * on clusters that predate those columns.
   */
  public static String baseWhereSql(
      RootCauseQueryBuilder.BindAccumulator acc,
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, emptyIfNull(projectId));
    acc.add(p1, emptyIfNull(screenName));
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    return "ProjectId = :"
        + p0
        + " AND PulseType = '"
        + APP_CLICK_PULSE_TYPE
        + "'"
        + " AND nullIf(trimBoth(ScreenName), '') = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  public static RootCauseQuerySpec buildBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "count() AS "
            + CLICK_VOLUME
            + ", "
            + TAP_COUNT_EXPR
            + " AS "
            + TAP_COUNT
            + ", "
            + RAGE_COUNT_EXPR
            + " AS "
            + RAGE_COUNT
            + ", "
            + DEAD_COUNT_EXPR
            + " AS "
            + DEAD_COUNT
            + ", "
            + BAD_FRUSTRATION_EXPR
            + " AS "
            + BAD_FRUSTRATION;
    String where = baseWhereSql(acc, projectId, screenName, startInclusive, endExclusive);
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_LOGS_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildBadFrustrationByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect = dimSelect + ", " + BAD_FRUSTRATION_EXPR + " AS " + BAD_FRUSTRATION;
    String where =
        appendDimensionFilters(
            baseWhereSql(acc, projectId, screenName, startInclusive, endExclusive), acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_LOGS_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  /**
   * Full metric row for a segment (GROUP BY all listed dimensions).
   */
  public static RootCauseQuerySpec buildSegmentQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      List<String> dimensionColumns,
      Map<String, String> dimensionFilters) {
    if (dimensionColumns == null || dimensionColumns.isEmpty()) {
      throw new IllegalArgumentException("dimensionColumns must be non-empty for segment query");
    }
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    StringBuilder metricSelect = new StringBuilder();
    for (String d : dimensionColumns) {
      if (metricSelect.length() > 0) {
        metricSelect.append(", ");
      }
      metricSelect.append(dimensionSelectAlias(d));
    }
    metricSelect.append(", count() AS ").append(CLICK_VOLUME);
    metricSelect.append(", ").append(TAP_COUNT_EXPR).append(" AS ").append(TAP_COUNT);
    metricSelect.append(", ").append(RAGE_COUNT_EXPR).append(" AS ").append(RAGE_COUNT);
    metricSelect.append(", ").append(DEAD_COUNT_EXPR).append(" AS ").append(DEAD_COUNT);
    metricSelect.append(", ").append(BAD_FRUSTRATION_EXPR).append(" AS ").append(BAD_FRUSTRATION);
    String where =
        appendDimensionFilters(
            baseWhereSql(acc, projectId, screenName, startInclusive, endExclusive), acc, dimensionFilters);
    // CH 25+: GROUP BY select aliases (Platform, …), not the coalesce expression in SELECT
    String groupBy = String.join(", ", dimensionColumns);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_LOGS_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + groupBy;
    return acc.toSpec(sql);
  }

  static String dimensionSelectAlias(String dimensionName) {
    return dimensionExpression(dimensionName) + " AS " + dimensionName;
  }

  static final String UNKNOWN_DIMENSION = "Unknown";

  /** Materialized dimension columns (same definitions as {@code otel.otel_logs} DDL). */
  public static String dimensionExpression(String dimensionName) {
    String col = switch (dimensionName) {
      case "Platform" -> "Platform";
      case "OsVersion" -> "OsVersion";
      case "AppVersion" -> "AppVersion";
      case "DeviceModel" -> "DeviceModel";
      case "NetworkProvider" -> "NetworkProvider";
      case "GeoState" -> "GeoState";
      default -> throw new IllegalArgumentException("Unknown Screen RCA dimension: " + dimensionName);
    };
    return "ifNull(nullIf(trimBoth(" + col + "), ''), '" + UNKNOWN_DIMENSION + "')";
  }

  private static String dimensionEqualityLhs(String dimensionName) {
    return "(" + dimensionExpression(dimensionName) + ")";
  }

  private static String appendDimensionFilters(
      String baseWhereSql,
      RootCauseQueryBuilder.BindAccumulator acc,
      Map<String, String> dimensionFilters) {
    if (dimensionFilters == null || dimensionFilters.isEmpty()) {
      return baseWhereSql;
    }
    StringBuilder sb = new StringBuilder(baseWhereSql);
    for (Map.Entry<String, String> e : dimensionFilters.entrySet()) {
      String pn = acc.nextName();
      acc.add(pn, emptyIfNull(e.getValue()));
      sb.append(" AND ").append(dimensionEqualityLhs(e.getKey())).append(" = :").append(pn);
    }
    return sb.toString();
  }

  private static String emptyIfNull(String s) {
    return s == null ? "" : s;
  }
}
