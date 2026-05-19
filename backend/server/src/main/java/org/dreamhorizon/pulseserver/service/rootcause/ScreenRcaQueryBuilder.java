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
  /** Segmentation driver: dead ∪ rage (one row can be both; counted once). */
  public static final String BAD_FRUSTRATION = "bad_frustration";

  // Metric ID constants for Screen RCA v2 problems
  public static final String CRASH_RATE             = "crash_rate";
  public static final String ANR_RATE               = "anr_rate";
  public static final String FROZEN_FRAME_RATE      = "frozen_frame_rate";
  public static final String SLOW_FRAME_RATE        = "slow_frame_rate";
  public static final String NETWORK_ERROR_RATE     = "network_error_rate";
  public static final String BAD_NETWORK_LATENCY_RATE    = "bad_network_latency_rate";
  public static final String BAD_SCREEN_LOAD_RATE        = "bad_screen_load_rate";
  public static final String BAD_SCREEN_INTERACTIVE_RATE = "bad_screen_interactive_rate";
  public static final String BAD_CLICKS_RATE             = "bad_clicks_rate";

  /** Users with screen load time above this threshold are counted as affected. */
  public static final long SCREEN_LOAD_BAD_THRESHOLD_MS        = 500L;
  /** Users with screen interactive time above this threshold are counted as affected. */
  public static final long SCREEN_INTERACTIVE_BAD_THRESHOLD_MS = 7300L;
  /** Users with network latency above this threshold are counted as affected. */
  public static final long NETWORK_LATENCY_BAD_THRESHOLD_MS    = 1000L;

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
            + BAD_FRUSTRATION
            + ", uniqIf(SessionId, ClickType = 'dead' OR Rage) AS affected_user_count";
    String where = baseWhereSql(acc, projectId, screenName, startInclusive, endExclusive);
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
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
    String metricSelect = dimSelect + ", " + BAD_FRUSTRATION_EXPR + " AS " + BAD_FRUSTRATION
        + ", uniqIf(SessionId, ClickType = 'dead' OR Rage) AS affected_user_count";
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
    String groupBy = dimensionColumns.stream().collect(Collectors.joining(", "));
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

  /** Materialized dimension columns (same definitions as {@code otel.otel_logs} DDL). */
  public static String dimensionExpression(String dimensionName) {
    return switch (dimensionName) {
      case "Platform" -> "Platform";
      case "OsVersion" -> "OsVersion";
      case "AppVersion" -> "AppVersion";
      case "DeviceModel" -> "DeviceModel";
      case "NetworkProvider" -> "NetworkProvider";
      case "GeoState" -> "GeoState";
      default -> throw new IllegalArgumentException("Unknown Screen RCA dimension: " + dimensionName);
    };
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

  // ===== Screen RCA v2: Crashes =====

  public static RootCauseQuerySpec buildCrashBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniq(UserId) AS affected_user_count";
    String where =
        buildStackTraceBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'device.crash'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildCrashByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect + ", uniq(UserId) AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildStackTraceBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'device.crash'"),
            acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: ANR =====

  public static RootCauseQuerySpec buildAnrBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniq(UserId) AS affected_user_count";
    String where =
        buildStackTraceBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'device.anr'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildAnrByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect + ", uniq(UserId) AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildStackTraceBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'device.anr'"),
            acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Network Failures =====

  public static RootCauseQuerySpec buildNetworkFailureBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniqIf(SessionId, PulseType LIKE 'network.4%' OR PulseType LIKE 'network.5%') AS affected_user_count";
    String where =
        buildLogsBaseWhereSqlWithPulseTypeLike(acc, projectId, screenName, startInclusive, endExclusive,
            "'network.%'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildNetworkFailureByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect
            + ", uniqIf(SessionId, PulseType LIKE 'network.4%' OR PulseType LIKE 'network.5%') AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildLogsBaseWhereSqlWithPulseTypeLike(acc, projectId, screenName, startInclusive, endExclusive,
                "'network.%'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Network Latency =====

  public static RootCauseQuerySpec buildNetworkLatencyBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + NETWORK_LATENCY_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        buildLogsBaseWhereSqlWithPulseTypeLike(acc, projectId, screenName, startInclusive, endExclusive,
            "'network.%'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildNetworkLatencyByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect
            + ", uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + NETWORK_LATENCY_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildLogsBaseWhereSqlWithPulseTypeLike(acc, projectId, screenName, startInclusive, endExclusive,
                "'network.%'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Helper methods for stack_trace_events queries =====

  private static String buildStackTraceBaseWhereSql(
      RootCauseQueryBuilder.BindAccumulator acc,
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String pulseTypeEq) {
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
        + " AND PulseType = "
        + pulseTypeEq
        + " AND ScreenName = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  // ===== Helper methods for otel_logs queries =====

  private static String buildLogsBaseWhereSql(
      RootCauseQueryBuilder.BindAccumulator acc,
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String pulseTypeEq) {
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
        + " AND PulseType = "
        + pulseTypeEq
        + " AND nullIf(trimBoth(ScreenName), '') = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  private static String buildLogsBaseWhereSqlWithPulseTypeLike(
      RootCauseQueryBuilder.BindAccumulator acc,
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String pulseTypeLike) {
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
        + " AND PulseType LIKE "
        + pulseTypeLike
        + " AND nullIf(trimBoth(ScreenName), '') = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  // ===== Screen RCA v2: Frozen Frames (otel_traces) =====

  public static RootCauseQuerySpec buildFrozenFrameBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where =
        buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'app.jank.frozen'");
    String sql =
        "SELECT uniq(SessionId) AS affected_user_count"
            + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildFrozenFrameByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where =
        appendDimensionFilters(
            buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'app.jank.frozen'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + dimensionSelectAlias(dimensionColumn)
            + ", uniq(SessionId) AS affected_user_count"
            + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE " + where
            + " GROUP BY " + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Slow Rendering (otel_traces) =====

  public static RootCauseQuerySpec buildSlowRenderingBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where =
        buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'app.jank.slow'");
    String sql =
        "SELECT uniq(SessionId) AS affected_user_count"
            + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildSlowRenderingByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where =
        appendDimensionFilters(
            buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'app.jank.slow'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + dimensionSelectAlias(dimensionColumn)
            + ", uniq(SessionId) AS affected_user_count"
            + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE " + where
            + " GROUP BY " + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Screen Load P95 (otel_traces) =====

  public static RootCauseQuerySpec buildScreenLoadBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + SCREEN_LOAD_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'screen_load'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildScreenLoadByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect
            + ", uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + SCREEN_LOAD_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'screen_load'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Screen Interactive P95 (otel_traces) =====

  public static RootCauseQuerySpec buildScreenInteractiveBaselineQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String metricSelect =
        "uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + SCREEN_INTERACTIVE_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
            "'screen_interactive'");
    String sql =
        "SELECT " + metricSelect + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildScreenInteractiveByDimensionQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      Map<String, String> dimensionFilters) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String dimSelect = dimensionSelectAlias(dimensionColumn);
    String metricSelect =
        dimSelect
            + ", uniqIf(SessionId, toFloat64(Duration) / 1e6 > " + SCREEN_INTERACTIVE_BAD_THRESHOLD_MS
            + ") AS affected_user_count";
    String where =
        appendDimensionFilters(
            buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
                "'screen_interactive'"), acc, dimensionFilters);
    String sql =
        "SELECT "
            + metricSelect
            + " FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE "
            + where
            + " GROUP BY "
            + dimensionColumn;
    return acc.toSpec(sql);
  }

  // ===== Helper methods for otel_traces queries =====

  private static String buildTracesBaseWhereSql(
      RootCauseQueryBuilder.BindAccumulator acc,
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String pulseTypeEq) {
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
        + " AND PulseType = "
        + pulseTypeEq
        + " AND nullIf(trimBoth(ScreenName), '') = :"
        + p1
        + " AND Timestamp >= toDateTime64(:"
        + p2
        + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:"
        + p3
        + ", 9, 'UTC')";
  }

  // ===== Screen RCA v2: Specific Issues Queries =====

  public static RootCauseQuerySpec buildCrashSpecificIssuesQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      String dimensionValue) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    String p4 = acc.nextName();
    acc.add(p0, emptyIfNull(projectId));
    acc.add(p1, emptyIfNull(screenName));
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    acc.add(p4, emptyIfNull(dimensionValue));
    String sql =
        "SELECT GroupId AS group_id, ExceptionMessage AS issue, uniq(UserId) AS cnt "
            + "FROM " + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE + " "
            + "WHERE ProjectId = :" + p0
            + " AND PulseType = 'device.crash'"
            + " AND ScreenName = :" + p1
            + " AND " + dimensionEqualityLhs(dimensionColumn) + " = :" + p4
            + " AND Timestamp >= toDateTime64(:" + p2 + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:" + p3 + ", 9, 'UTC')"
            + " GROUP BY group_id, issue ORDER BY cnt DESC LIMIT 3";
    return acc.toSpec(sql);
  }

  public static RootCauseQuerySpec buildAnrSpecificIssuesQuery(
      String projectId,
      String screenName,
      Instant startInclusive,
      Instant endExclusive,
      String dimensionColumn,
      String dimensionValue) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    String p4 = acc.nextName();
    acc.add(p0, emptyIfNull(projectId));
    acc.add(p1, emptyIfNull(screenName));
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    acc.add(p4, emptyIfNull(dimensionValue));
    String sql =
        "SELECT GroupId AS group_id, Title AS thread, uniq(UserId) AS cnt "
            + "FROM " + ClickhouseConstants.STACK_TRACE_EVENTS_TABLE + " "
            + "WHERE ProjectId = :" + p0
            + " AND PulseType = 'device.anr'"
            + " AND ScreenName = :" + p1
            + " AND " + dimensionEqualityLhs(dimensionColumn) + " = :" + p4
            + " AND Timestamp >= toDateTime64(:" + p2 + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:" + p3 + ", 9, 'UTC')"
            + " GROUP BY group_id, thread ORDER BY cnt DESC LIMIT 3";
    return acc.toSpec(sql);
  }

  // ===== Screen RCA v2: Percentile queries =====

  public static final String P50_MS = "p50_ms";
  public static final String P95_MS = "p95_ms";

  public static RootCauseQuerySpec buildScreenLoadPercentilesQuery(
      String projectId, String screenName, Instant startInclusive, Instant endExclusive) {
    return buildTracesPercentilesQuery(projectId, screenName, startInclusive, endExclusive,
        "'screen_load'");
  }

  public static RootCauseQuerySpec buildScreenInteractivePercentilesQuery(
      String projectId, String screenName, Instant startInclusive, Instant endExclusive) {
    return buildTracesPercentilesQuery(projectId, screenName, startInclusive, endExclusive,
        "'screen_interactive'");
  }

  public static RootCauseQuerySpec buildNetworkLatencyPercentilesQuery(
      String projectId, String screenName, Instant startInclusive, Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where = buildLogsBaseWhereSqlWithPulseTypeLike(acc, projectId, screenName,
        startInclusive, endExclusive, "'network.%'");
    String sql = "SELECT quantile(0.5)(toFloat64(Duration) / 1e6) AS " + P50_MS
        + ", quantile(0.95)(toFloat64(Duration) / 1e6) AS " + P95_MS
        + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }

  private static RootCauseQuerySpec buildTracesPercentilesQuery(
      String projectId, String screenName, Instant startInclusive, Instant endExclusive,
      String pulseTypeEq) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String where = buildTracesBaseWhereSql(acc, projectId, screenName, startInclusive, endExclusive,
        pulseTypeEq);
    String sql = "SELECT quantile(0.5)(toFloat64(Duration) / 1e6) AS " + P50_MS
        + ", quantile(0.95)(toFloat64(Duration) / 1e6) AS " + P95_MS
        + " FROM " + ClickhouseConstants.OTEL_TRACES_TABLE + " WHERE " + where;
    return acc.toSpec(sql);
  }
}