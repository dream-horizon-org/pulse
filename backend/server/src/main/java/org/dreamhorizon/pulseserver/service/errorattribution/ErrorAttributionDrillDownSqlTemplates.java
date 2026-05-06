package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.LinkedHashMap;
import java.util.Map;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * Readable ClickHouse for error-attribution Mode A drills.
 *
 * <p>Placeholders are {@code {{TOKEN}}} strings; {@code :{{P0}}} becomes {@code :rca_p0} after
 * substitution with binding names produced by {@link org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder.BindAccumulator}.
 */
final class ErrorAttributionDrillDownSqlTemplates {

  private ErrorAttributionDrillDownSqlTemplates() {
  }

  /**
   * Relative-risk ORDER BY (finite RR, infinite-style branch). Expects aliases {@code n_treated}, {@code
   * n_control}, etc.
   */
  static final String FAST_DRILL_RELATIVE_RISK_ORDER =
      "multiIf("
          + "n_treated = 0, -1e200, "
          + "n_control <= 0, 1e300, "
          + "n_control_low > 0 AND n_control > 0, "
          + "round( "
          + "(toFloat64(n_treated_low) / toFloat64(n_treated)) "
          + "/ (toFloat64(n_control_low) / toFloat64(n_control)), "
          + "4), "
          + "n_treated_low > 0, 1e301, "
          + "-1e200) ";

  private static final String SESSION_METRICS_AND_GLOBALS =
      """
          session_metrics AS (
            SELECT
              SessionId,
              max(if({{POOR_PREDICATE}}, 1, 0)) AS is_poor,
              minIf(Timestamp, {{POOR_PREDICATE}}) AS poor_ts,
              maxIf(Timestamp + toIntervalNanosecond(greatest(0, toInt64(ifNull(Duration, 0)))),
                    {{POOR_PREDICATE}}) AS poor_end_ts
            FROM {{OTEL_TRACES}}
            PREWHERE ProjectId = :{{P0}}
              AND PulseType = '{{INTERACTION_PULSE_TYPE}}'
              AND SpanName = :{{P1}}
              AND Timestamp >= toDateTime64(:{{P2}}, 9, 'UTC')
              AND Timestamp < toDateTime64(:{{P3}}, 9, 'UTC')
            GROUP BY SessionId
            HAVING SessionId != ''
          ),
          globals AS (
            SELECT uniq(SessionId) AS n_u,
                   toUInt64(sum(is_poor)) AS n_poor_u
            FROM session_metrics
          ),
          """;

  private static final String STACK_OPTIMIZED_QUERY =
      """
          WITH
          {{SESSION_METRICS_AND_GLOBALS}}\
          error_sessions AS (
          {{ERROR_SESSION_SELECT}}
            FROM {{STACKS_TABLE}} AS ss
            INNER JOIN session_metrics AS sm ON ss.SessionId = sm.SessionId
            PREWHERE ss.ProjectId = :{{P0}}
              AND ss.PulseType = '{{STACK_PULSE_TYPE}}'
              AND ss.Timestamp >= toDateTime64(:{{P2}}, 9, 'UTC')
              AND ss.Timestamp < toDateTime64(:{{P3}}, 9, 'UTC')
          {{GROUP_BY_STACK}}
          ),
          ranked_rows AS (
            SELECT
          {{SELECT_RANKED_DIMENSIONS}}\
              uniq(es.SessionId) AS n_treated,
              {{UNIQ_N_TREATED_LOW}} AS n_treated_low,
              (any(g.n_u) - uniq(es.SessionId)) AS n_control,
              (any(g.n_poor_u) - {{UNIQ_N_TREATED_LOW}}) AS n_control_low,
              any(g.n_u) AS n_u,
              any(g.n_poor_u) AS n_poor_u
            FROM error_sessions AS es
            CROSS JOIN globals AS g
          {{GROUP_BY_RANK}}
          )
          SELECT
          {{SELECT_OUTER_DIMENSIONS}}\
            ranked_rows.n_treated AS n_treated,
            ranked_rows.n_treated_low AS n_treated_low,
            ranked_rows.n_control AS n_control,
            ranked_rows.n_control_low AS n_control_low,
            ranked_rows.n_u AS n_u,
            ranked_rows.n_poor_u AS n_poor_u
          FROM ranked_rows
          WHERE ranked_rows.n_treated >= toInt64(:{{P4}})
            AND (toInt64(:{{P5}}) = 0 OR ranked_rows.n_control >= toInt64(:{{P5}}))
          ORDER BY {{FAST_DRILL_RELATIVE_RISK_ORDER}}DESC,
                   ranked_rows.n_treated DESC
          LIMIT toInt64(:{{P6}})
          """;

  private static final String API_OPTIMIZED_QUERY =
      """
          WITH
          {{SESSION_METRICS_AND_GLOBALS}}\
          network_sessions AS (
            SELECT
              SessionId,
              {{URL_EXPR}} AS drill_url,
              {{GQL_NAME_EXPR}} AS drill_gql_name,
              {{GQL_TYPE_EXPR}} AS drill_gql_type,
              {{METHOD_EXPR}} AS drill_http_method,
              {{STATUS_EXPR}} AS drill_http_status
          {{NETWORK_SELECT_TAIL}}\
              AND Timestamp >= toDateTime64(:{{P2}}, 9, 'UTC')
              AND Timestamp < toDateTime64(:{{P3}}, 9, 'UTC')
              AND {{CH_PULSE_TYPE_NETWORK_PREDICATE}}
              AND {{CH_STATUS_CODE_EQUALS_ERROR}}
            GROUP BY SessionId, drill_url, drill_gql_name, drill_gql_type, drill_http_method,\
           drill_http_status
          ),
          ranked_rows AS (
            SELECT
              ns.drill_url AS url,
              ns.drill_gql_name AS graphql_operation_name,
              ns.drill_gql_type AS graphql_operation_type,
              ns.drill_http_method AS http_method,
              ns.drill_http_status AS http_status_code,
              uniq(ns.SessionId) AS n_treated,
              {{UNIQ_N_TREATED_LOW}} AS n_treated_low,
              (any(g.n_u) - uniq(ns.SessionId)) AS n_control,
              (any(g.n_poor_u) - {{UNIQ_N_TREATED_LOW}}) AS n_control_low,
              any(g.n_u) AS n_u,
              any(g.n_poor_u) AS n_poor_u
            FROM network_sessions AS ns
            INNER JOIN session_metrics AS ta ON ns.SessionId = ta.SessionId
            CROSS JOIN globals AS g
            GROUP BY ns.drill_url, ns.drill_gql_name, ns.drill_gql_type, ns.drill_http_method,\
           ns.drill_http_status
          )
          SELECT
            ranked_rows.url AS url,
            ranked_rows.graphql_operation_name AS graphql_operation_name,
            ranked_rows.graphql_operation_type AS graphql_operation_type,
            ranked_rows.http_method AS http_method,
            ranked_rows.http_status_code AS http_status_code,
            ranked_rows.n_treated AS n_treated,
            ranked_rows.n_treated_low AS n_treated_low,
            ranked_rows.n_control AS n_control,
            ranked_rows.n_control_low AS n_control_low,
            ranked_rows.n_u AS n_u,
            ranked_rows.n_poor_u AS n_poor_u
          FROM ranked_rows
          WHERE ranked_rows.n_treated >= toInt64(:{{P4}})
            AND (toInt64(:{{P5}}) = 0 OR ranked_rows.n_control >= toInt64(:{{P5}}))
          ORDER BY {{FAST_DRILL_RELATIVE_RISK_ORDER}}DESC,
                   ranked_rows.n_treated DESC
          LIMIT toInt64(:{{P6}})
          """;

  static String applySessionMetricsAndGlobals(
      String traces,
      String interactionType,
      String poorPredicate,
      String p0,
      String p1,
      String p2,
      String p3) {
    return substitute(
        SESSION_METRICS_AND_GLOBALS,
        Map.of(
            "POOR_PREDICATE",
            poorPredicate,
            "OTEL_TRACES",
            traces,
            "INTERACTION_PULSE_TYPE",
            interactionType,
            "P0",
            p0,
            "P1",
            p1,
            "P2",
            p2,
            "P3",
            p3));
  }

  static String stackOptimizedSql(
      boolean tripleKey,
      ErrorAttributionDrillDownQueryBuilder.DrillDownQueryParams params,
      String traces,
      String stacksTable,
      String interactionType,
      String stackPulseType,
      String poorPredicate,
      String p0,
      String p1,
      String p2,
      String p3,
      String p4,
      String p5,
      String p6) {

    String uniqTreatedLow =
        params.issueMustPrecedePoor()
            ? "uniqIf(es.SessionId, es.is_poor = 1 AND es.first_issue_ts < es.poor_end_ts)"
            : "uniqIf(es.SessionId, es.is_poor = 1)";

    final String errorSessionSelect =
        tripleKey
            ? (params.issueMustPrecedePoor()
            ? stackErrSelTripleTemporal()
            : stackErrSelTriplePlain())
            : (params.issueMustPrecedePoor()
            ? stackErrSelDoubleTemporal()
            : stackErrSelDoublePlain());

    String groupByStack =
        tripleKey
            ? "  GROUP BY ss.SessionId, ss.GroupId, ss.Title, ss.ExceptionType"
            : "  GROUP BY ss.SessionId, ss.GroupId, ss.Title";

    String groupByRank =
        tripleKey
            ? "  GROUP BY es.GroupId, es.Title, es.ExceptionType\n"
            : "  GROUP BY es.GroupId, es.Title\n";

    String rankedDims =
        tripleKey
            ? ("""
                es.GroupId AS group_id,
                es.Title AS title,
                es.ExceptionType AS exception_type,
            """)
            : """
                es.GroupId AS group_id,
                es.Title AS title,
            """;

    String outerDims =
        tripleKey
            ? ("""
              ranked_rows.group_id AS group_id,
              ranked_rows.title AS title,
              ranked_rows.exception_type AS exception_type,
            """)
            : """
              ranked_rows.group_id AS group_id,
              ranked_rows.title AS title,
            """;

    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put(
        "SESSION_METRICS_AND_GLOBALS",
        applySessionMetricsAndGlobals(traces, interactionType, poorPredicate, p0, p1, p2, p3));
    m.put("ERROR_SESSION_SELECT", errorSessionSelect);
    m.put("STACKS_TABLE", stacksTable);
    m.put("P0", p0);
    m.put("P2", p2);
    m.put("P3", p3);
    m.put("P4", p4);
    m.put("P5", p5);
    m.put("P6", p6);
    m.put("STACK_PULSE_TYPE", stackPulseType);
    m.put("GROUP_BY_STACK", groupByStack);
    m.put("GROUP_BY_RANK", groupByRank);
    m.put("SELECT_RANKED_DIMENSIONS", rankedDims);
    m.put("SELECT_OUTER_DIMENSIONS", outerDims);
    m.put("UNIQ_N_TREATED_LOW", uniqTreatedLow);
    m.put("FAST_DRILL_RELATIVE_RISK_ORDER", FAST_DRILL_RELATIVE_RISK_ORDER);
    return substitute(STACK_OPTIMIZED_QUERY, m);
  }

  private static String stackErrSelTripleTemporal() {
    return """
        SELECT
            ss.SessionId, ss.GroupId, ss.Title, ss.ExceptionType,
            min(ss.Timestamp) AS first_issue_ts,
            any(sm.is_poor) AS is_poor,
            any(sm.poor_ts) AS poor_ts,
            any(sm.poor_end_ts) AS poor_end_ts""";
  }

  private static String stackErrSelTriplePlain() {
    return """
        SELECT
            ss.SessionId, ss.GroupId, ss.Title, ss.ExceptionType,
            any(sm.is_poor) AS is_poor,
            any(sm.poor_ts) AS poor_ts,
            any(sm.poor_end_ts) AS poor_end_ts""";
  }

  private static String stackErrSelDoubleTemporal() {
    return """
        SELECT
            ss.SessionId, ss.GroupId, ss.Title,
            min(ss.Timestamp) AS first_issue_ts,
            any(sm.is_poor) AS is_poor,
            any(sm.poor_ts) AS poor_ts,
            any(sm.poor_end_ts) AS poor_end_ts""";
  }

  private static String stackErrSelDoublePlain() {
    return """
        SELECT
            ss.SessionId, ss.GroupId, ss.Title,
            any(sm.is_poor) AS is_poor,
            any(sm.poor_ts) AS poor_ts,
            any(sm.poor_end_ts) AS poor_end_ts""";
  }

  static String apiOptimizedSql(
      ErrorAttributionDrillDownQueryBuilder.DrillDownQueryParams params,
      String traces,
      String interactionType,
      String poorPredicate,
      String urlExpr,
      String gqlNameExpr,
      String gqlTypeExpr,
      String methodExpr,
      String statusExpr,
      String p0,
      String p1,
      String p2,
      String p3,
      String p4,
      String p5,
      String p6) {

    String uniqTreatedLow =
        params.issueMustPrecedePoor()
            ? "uniqIf(ns.SessionId, ta.is_poor = 1 AND ns.first_endpoint_error_ts < ta.poor_end_ts)"
            : "uniqIf(ns.SessionId, ta.is_poor = 1)";

    final String networkSelectTail =
        params.issueMustPrecedePoor()
            ? substitute(
            """
                ,
                    min(Timestamp) AS first_endpoint_error_ts
                  FROM {{OTEL_TRACES}}
                  WHERE ProjectId = :{{P0}}
                    AND SessionId IN (SELECT SessionId FROM session_metrics)
                """,
            Map.of("OTEL_TRACES", traces, "P0", p0))
            : substitute(
            """
                  FROM {{OTEL_TRACES}}
                  WHERE ProjectId = :{{P0}}
                    AND SessionId IN (SELECT SessionId FROM session_metrics)
                """,
            Map.of("OTEL_TRACES", traces, "P0", p0));

    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put(
        "SESSION_METRICS_AND_GLOBALS",
        applySessionMetricsAndGlobals(traces, interactionType, poorPredicate, p0, p1, p2, p3));
    m.put("URL_EXPR", urlExpr);
    m.put("GQL_NAME_EXPR", gqlNameExpr);
    m.put("GQL_TYPE_EXPR", gqlTypeExpr);
    m.put("METHOD_EXPR", methodExpr);
    m.put("STATUS_EXPR", statusExpr);
    m.put("NETWORK_SELECT_TAIL", networkSelectTail + "\n");
    m.put("P2", p2);
    m.put("P3", p3);
    m.put("P4", p4);
    m.put("P5", p5);
    m.put("P6", p6);
    m.put(
        "CH_PULSE_TYPE_NETWORK_PREDICATE",
        ClickhouseConstants.CH_PULSE_TYPE_NETWORK_LIKE_PREDICATE);
    m.put(
        "CH_STATUS_CODE_EQUALS_ERROR",
        ClickhouseConstants.CH_STATUS_CODE_EQUALS_ERROR);
    m.put("UNIQ_N_TREATED_LOW", uniqTreatedLow);
    m.put("FAST_DRILL_RELATIVE_RISK_ORDER", FAST_DRILL_RELATIVE_RISK_ORDER);
    return substitute(API_OPTIMIZED_QUERY, m);
  }

  private static String substitute(String template, Map<String, String> keysToValues) {
    String sql = template;
    for (var e : keysToValues.entrySet()) {
      sql = sql.replace("{{" + e.getKey() + "}}", e.getValue());
    }
    return sql;
  }
}
