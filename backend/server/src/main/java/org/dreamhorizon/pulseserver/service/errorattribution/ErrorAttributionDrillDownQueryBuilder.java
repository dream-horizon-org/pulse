package org.dreamhorizon.pulseserver.service.errorattribution;

import java.time.Instant;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.interaction.InteractionTelemetryConstants;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;

/**
 * Per-issue / per-endpoint drill-down for one error-attribution signal: **Mode A** — arms over full
 * universe {@code U}, candidate keys from sessions in {@code U}, gates on {@code n_treated} and
 * optionally {@code n_control}, ranked by finite RR (then infinite RR, then {@code n_treated}).
 */
public final class ErrorAttributionDrillDownQueryBuilder {

  /** Kept for tests and alignment with {@link RootCauseConfig#DEFAULT_ISSUE_DRILL_DOWN_LIMIT}. */
  static final int DRILL_DOWN_LIMIT = RootCauseConfig.DEFAULT_ISSUE_DRILL_DOWN_LIMIT;
  /** Per-signal SQL cap; aligns with {@link RootCauseConfig#DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT}. */
  static final int DRILL_DOWN_CANDIDATE_LIMIT = RootCauseConfig.DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT;

  public record DrillDownQueryParams(
      int minTreatedSessions,
      int minControlSessions,
      int candidateRowLimit,
      boolean issueMustPrecedePoor) {

    public static DrillDownQueryParams fromRootCauseConfig(RootCauseConfig rootCauseConfig) {
      RootCauseConfig c = RootCauseConfig.withDefaults(rootCauseConfig);
      return new DrillDownQueryParams(
          c.getMinTreatedSessionsForIssueAttribution(),
          c.getMinControlSessionsForIssueAttribution(),
          c.getIssueDrillDownCandidateLimit(),
          Boolean.TRUE.equals(c.getIssueMustPrecedePoor()));
    }
  }

  private ErrorAttributionDrillDownQueryBuilder() {
  }

  public static RootCauseQuerySpec build(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      ErrorAttributionDrillDownSignal signal,
      DrillDownQueryParams params) {
    return switch (signal) {
      case crash -> buildStack(projectId, interactionName, startInclusive, endExclusive, "device.crash", false, params);
      case anr -> buildStack(projectId, interactionName, startInclusive, endExclusive, "device.anr", false, params);
      case non_fatal ->
          buildStack(projectId, interactionName, startInclusive, endExclusive, "non_fatal", true, params);
      case api -> buildApi(projectId, interactionName, startInclusive, endExclusive, params);
    };
  }

  private static RootCauseQuerySpec buildStack(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      String stackPulseType,
      boolean tripleKey,
      DrillDownQueryParams params) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    String p4 = acc.nextName();
    String p5 = acc.nextName();
    String p6 = acc.nextName();
    acc.add(p0, projectId == null ? "" : projectId);
    acc.add(p1, interactionName == null ? "" : interactionName);
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    acc.add(p4, params.minTreatedSessions());
    acc.add(p5, params.minControlSessions());
    acc.add(p6, params.candidateRowLimit());

    String traces = ClickhouseConstants.OTEL_TRACES_TABLE;
    String stacks = ClickhouseConstants.STACK_TRACE_EVENTS_TABLE;
    String interactionType = InteractionTelemetryConstants.INTERACTION_PULSE_TYPE;

    String withCommon =
        "WITH "
            + "u_sessions AS ( "
            + "SELECT DISTINCT SessionId FROM "
            + traces
            + " WHERE ProjectId = :"
            + p0
            + " AND PulseType = '"
            + interactionType
            + "'"
            + " AND SpanName = :"
            + p1
            + " AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC')"
            + " AND SessionId != '' "
            + "), "
            + "trace_agg AS ( "
            + "SELECT "
            + "SessionId, "
            + "maxIf(1, PulseType = '"
            + interactionType
            + "'"
            + " AND SpanName = :"
            + p1
            + " AND ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor') AS is_low, "
            + "minIf(Timestamp, PulseType = '"
            + interactionType
            + "' AND SpanName = :"
            + p1
            + " AND ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor') AS poor_ts "
            + "FROM "
            + traces
            + " WHERE ProjectId = :"
            + p0
            + " AND SessionId IN (SELECT SessionId FROM u_sessions) "
            + "AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC') "
            + "AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC') "
            + "GROUP BY SessionId "
            + "), "
            + "universe AS ( SELECT uniqCombined64(SessionId) AS n_u FROM u_sessions ), "
            + "poor_tot AS ( SELECT toUInt64(sum(is_low)) AS n_poor_u FROM trace_agg ), ";

    String stackSessionsSelect =
        tripleKey
            ? (params.issueMustPrecedePoor()
                ? "SELECT SessionId, GroupId, Title, ExceptionType, min(Timestamp) AS first_issue_ts FROM "
                : "SELECT SessionId, GroupId, Title, ExceptionType FROM ")
            : (params.issueMustPrecedePoor()
                ? "SELECT SessionId, GroupId, Title, min(Timestamp) AS first_issue_ts FROM "
                : "SELECT SessionId, GroupId, Title FROM ");

    String stackSessionsGroupBy =
        tripleKey ? "GROUP BY SessionId, GroupId, Title, ExceptionType " : "GROUP BY SessionId, GroupId, Title ";

    String nTreatedLowAgg =
        params.issueMustPrecedePoor()
            ? ("uniqCombined64If(ss.SessionId, ta.is_low = 1 AND ta.poor_ts >= toDateTime64(:"
                + p2
                + ", 9, 'UTC') AND ta.poor_ts < toDateTime64(:"
                + p3
                + ", 9, 'UTC') AND ss.first_issue_ts < ta.poor_ts) AS n_treated_low ")
            : "uniqCombined64If(ss.SessionId, ta.is_low = 1) AS n_treated_low ";

    String keyStatsGroupBy =
        tripleKey ? "GROUP BY ss.GroupId, ss.Title, ss.ExceptionType " : "GROUP BY ss.GroupId, ss.Title ";

    String selectDims =
        tripleKey
            ? "ss.GroupId AS group_id, ss.Title AS title, ss.ExceptionType AS exception_type, "
            : "ss.GroupId AS group_id, ss.Title AS title, ";

    String selectDimsOuter =
        tripleKey
            ? "ks.group_id AS group_id, ks.title AS title, ks.exception_type AS exception_type, "
            : "ks.group_id AS group_id, ks.title AS title, ";

    String rrSort =
        "multiIf("
            + "ks.n_treated = 0, -1e200, "
            + "(ut.n_u - ks.n_treated) <= 0, 1e300, "
            + "(pt.n_poor_u - ks.n_treated_low) > 0 AND (ut.n_u - ks.n_treated) > 0, "
            + "round( "
            + "(toFloat64(ks.n_treated_low) / toFloat64(ks.n_treated)) "
            + "/ (toFloat64(pt.n_poor_u - ks.n_treated_low) / toFloat64(ut.n_u - ks.n_treated)), "
            + "4), "
            + "ks.n_treated_low > 0, 1e301, "
            + "-1e200) ";

    String sql =
        withCommon
            + "stack_sessions AS ( "
            + stackSessionsSelect
            + stacks
            + " WHERE ProjectId = :"
            + p0
            + " AND SessionId IN (SELECT SessionId FROM u_sessions) "
            + "AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC') "
            + "AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC') "
            + "AND PulseType = '"
            + stackPulseType
            + "' "
            + stackSessionsGroupBy
            + "), "
            + "key_stats AS ( "
            + "SELECT "
            + selectDims
            + "uniqCombined64(ss.SessionId) AS n_treated, "
            + nTreatedLowAgg
            + "FROM stack_sessions ss "
            + "INNER JOIN trace_agg ta ON ss.SessionId = ta.SessionId "
            + keyStatsGroupBy
            + ") "
            + "SELECT "
            + selectDimsOuter
            + "ks.n_treated AS n_treated, "
            + "ks.n_treated_low AS n_treated_low, "
            + "(ut.n_u - ks.n_treated) AS n_control, "
            + "(pt.n_poor_u - ks.n_treated_low) AS n_control_low, "
            + "ut.n_u AS n_u, "
            + "pt.n_poor_u AS n_poor_u "
            + "FROM key_stats ks "
            + "CROSS JOIN universe ut "
            + "CROSS JOIN poor_tot pt "
            + "WHERE ks.n_treated >= toInt64(:"
            + p4
            + ") "
            + "AND (toInt64(:"
            + p5
            + ") = 0 OR (ut.n_u - ks.n_treated) >= toInt64(:"
            + p5
            + ")) "
            + "ORDER BY "
            + rrSort
            + "DESC, ks.n_treated DESC "
            + "LIMIT toInt64(:"
            + p6
            + ")";

    return acc.toSpec(sql);
  }

  private static RootCauseQuerySpec buildApi(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      DrillDownQueryParams params) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    String p4 = acc.nextName();
    String p5 = acc.nextName();
    String p6 = acc.nextName();
    acc.add(p0, projectId == null ? "" : projectId);
    acc.add(p1, interactionName == null ? "" : interactionName);
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    acc.add(p4, params.minTreatedSessions());
    acc.add(p5, params.minControlSessions());
    acc.add(p6, params.candidateRowLimit());

    String traces = ClickhouseConstants.OTEL_TRACES_TABLE;
    String interactionType = InteractionTelemetryConstants.INTERACTION_PULSE_TYPE;

    String urlExpr = "ifNull(SpanAttributes['http.url'], '')";
    String gqlNameExpr = "ifNull(SpanAttributes['graphql.operation.name'], '')";
    String gqlTypeExpr = "ifNull(SpanAttributes['graphql.operation.type'], '')";

    String rrSort =
        "multiIf("
            + "ks.n_treated = 0, -1e200, "
            + "(ut.n_u - ks.n_treated) <= 0, 1e300, "
            + "(pt.n_poor_u - ks.n_treated_low) > 0 AND (ut.n_u - ks.n_treated) > 0, "
            + "round( "
            + "(toFloat64(ks.n_treated_low) / toFloat64(ks.n_treated)) "
            + "/ (toFloat64(pt.n_poor_u - ks.n_treated_low) / toFloat64(ut.n_u - ks.n_treated)), "
            + "4), "
            + "ks.n_treated_low > 0, 1e301, "
            + "-1e200) ";

    String sql =
        "WITH "
            + "u_sessions AS ( "
            + "SELECT DISTINCT SessionId FROM "
            + traces
            + " WHERE ProjectId = :"
            + p0
            + " AND PulseType = '"
            + interactionType
            + "'"
            + " AND SpanName = :"
            + p1
            + " AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC')"
            + " AND SessionId != '' "
            + "), "
            + "trace_agg AS ( "
            + "SELECT "
            + "SessionId, "
            + "maxIf(1, PulseType = '"
            + interactionType
            + "'"
            + " AND SpanName = :"
            + p1
            + " AND ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor') AS is_low, "
            + "minIf(Timestamp, PulseType = '"
            + interactionType
            + "' AND SpanName = :"
            + p1
            + " AND ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor') AS poor_ts "
            + "FROM "
            + traces
            + " WHERE ProjectId = :"
            + p0
            + " AND SessionId IN (SELECT SessionId FROM u_sessions) "
            + "AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC') "
            + "AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC') "
            + "GROUP BY SessionId "
            + "), "
            + "universe AS ( SELECT uniqCombined64(SessionId) AS n_u FROM u_sessions ), "
            + "poor_tot AS ( SELECT toUInt64(sum(is_low)) AS n_poor_u FROM trace_agg ), "
            + "network_sessions AS ( "
            + "SELECT SessionId, "
            + urlExpr
            + " AS drill_url, "
            + gqlNameExpr
            + " AS drill_gql_name, "
            + gqlTypeExpr
            + " AS drill_gql_type"
            + (params.issueMustPrecedePoor() ? ", min(Timestamp) AS first_endpoint_error_ts " : " ")
            + "FROM "
            + traces
            + " WHERE ProjectId = :"
            + p0
            + " AND SessionId IN (SELECT SessionId FROM u_sessions) "
            + "AND Timestamp >= toDateTime64(:"
            + p2
            + ", 9, 'UTC') "
            + "AND Timestamp < toDateTime64(:"
            + p3
            + ", 9, 'UTC') "
            + "AND PulseType LIKE 'network.%' "
            + "AND StatusCode = 'Error' "
            + "GROUP BY SessionId, drill_url, drill_gql_name, drill_gql_type "
            + "), "
            + "key_stats AS ( "
            + "SELECT "
            + "ns.drill_url AS url, "
            + "ns.drill_gql_name AS graphql_operation_name, "
            + "ns.drill_gql_type AS graphql_operation_type, "
            + "uniqCombined64(ns.SessionId) AS n_treated, "
            + (params.issueMustPrecedePoor()
                ? ("uniqCombined64If(ns.SessionId, ta.is_low = 1 AND ta.poor_ts >= toDateTime64(:"
                    + p2
                    + ", 9, 'UTC') AND ta.poor_ts < toDateTime64(:"
                    + p3
                    + ", 9, 'UTC') AND ns.first_endpoint_error_ts < ta.poor_ts) AS n_treated_low ")
                : "uniqCombined64If(ns.SessionId, ta.is_low = 1) AS n_treated_low ")
            + "FROM network_sessions ns "
            + "INNER JOIN trace_agg ta ON ns.SessionId = ta.SessionId "
            + "GROUP BY ns.drill_url, ns.drill_gql_name, ns.drill_gql_type "
            + ") "
            + "SELECT "
            + "ks.url AS url, "
            + "ks.graphql_operation_name AS graphql_operation_name, "
            + "ks.graphql_operation_type AS graphql_operation_type, "
            + "ks.n_treated AS n_treated, "
            + "ks.n_treated_low AS n_treated_low, "
            + "(ut.n_u - ks.n_treated) AS n_control, "
            + "(pt.n_poor_u - ks.n_treated_low) AS n_control_low, "
            + "ut.n_u AS n_u, "
            + "pt.n_poor_u AS n_poor_u "
            + "FROM key_stats ks "
            + "CROSS JOIN universe ut "
            + "CROSS JOIN poor_tot pt "
            + "WHERE ks.n_treated >= toInt64(:"
            + p4
            + ") "
            + "AND (toInt64(:"
            + p5
            + ") = 0 OR (ut.n_u - ks.n_treated) >= toInt64(:"
            + p5
            + ")) "
            + "ORDER BY "
            + rrSort
            + "DESC, ks.n_treated DESC "
            + "LIMIT toInt64(:"
            + p6
            + ")";

    return acc.toSpec(sql);
  }
}
