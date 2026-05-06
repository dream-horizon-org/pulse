package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownQueryBuilder.DrillDownQueryParams;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;
import org.junit.jupiter.api.Test;

class ErrorAttributionDrillDownQueryBuilderTest {

  private static final Instant START = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant END = Instant.parse("2026-04-08T12:00:00Z");

  private static final DrillDownQueryParams PARAMS_OFF =
      new DrillDownQueryParams(
          RootCauseConfig.DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION,
          RootCauseConfig.DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION,
          ErrorAttributionDrillDownQueryBuilder.DRILL_DOWN_CANDIDATE_LIMIT,
          false);

  private static final DrillDownQueryParams PARAMS_TEMPORAL_ON =
      new DrillDownQueryParams(
          RootCauseConfig.DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION,
          RootCauseConfig.DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION,
          ErrorAttributionDrillDownQueryBuilder.DRILL_DOWN_CANDIDATE_LIMIT,
          true);

  @Test
  void crashUsesOptimizedStackSql() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.crash, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("session_metrics AS");
    assertThat(sql).contains("error_sessions AS");
    assertThat(sql).contains("uniq(es.SessionId)");
    assertThat(sql).contains("uniqIf(es.SessionId");
    assertThat(sql).contains("PulseType = 'device.crash'");
    assertThat(sql).contains("LIMIT toInt64(:");
    assertThat(sql).doesNotContain("stack_sessions AS");
    assertThat(sql).doesNotContain("trace_agg AS");
    assertThat(sql).doesNotContain("eligible_stack_groups");
    assertThat(sql).doesNotContain("poor_sessions AS");
    assertSharedBinds(spec);
  }

  @Test
  void crashWhenIssueMustPrecedePoor_usesPoorEndTsAgainstFirstIssueTs() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.crash, PARAMS_TEMPORAL_ON);
    String sql = spec.sql();
    assertThat(sql).contains("min(ss.Timestamp) AS first_issue_ts");
    assertThat(sql).contains("first_issue_ts < es.poor_end_ts");
    assertThat(sql).contains("maxIf(Timestamp + toIntervalNanosecond");
    assertThat(sql).doesNotContain("ta.poor_max_duration_ns");
    assertSharedBinds(spec);
  }

  @Test
  void nonFatalUsesOptimizedTripleKeyStackSql() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.non_fatal, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("session_metrics AS");
    assertThat(sql).contains("error_sessions AS");
    assertThat(sql).contains("uniq(es.SessionId)");
    assertThat(sql).contains("uniqIf(es.SessionId");
    assertThat(sql).contains("PulseType = 'non_fatal'");
    assertThat(sql).doesNotContain("uniqCombined64(ss.SessionId)");
    assertSharedBinds(spec);
  }

  @Test
  void nonFatalWhenIssueMustPrecedePoor_usesPoorEndTsAgainstFirstIssueTs() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.non_fatal, PARAMS_TEMPORAL_ON);
    String sql = spec.sql();
    assertThat(sql).contains("min(ss.Timestamp) AS first_issue_ts");
    assertThat(sql).contains("first_issue_ts < es.poor_end_ts");
    assertThat(sql).contains("maxIf(Timestamp + toIntervalNanosecond");
    assertThat(sql).doesNotContain("ta.poor_max_duration_ns");
    assertSharedBinds(spec);
  }

  @Test
  void apiUsesOptimizedNetworkSessionsAndSessionMetrics() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.api, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("session_metrics AS");
    assertThat(sql).contains("globals AS");
    assertThat(sql).contains("network_sessions AS");
    assertThat(sql).contains("ranked_rows AS");
    assertThat(sql).contains("uniq(ns.SessionId)");
    assertThat(sql).contains("SpanAttributes['http.url']");
    assertThat(sql).contains("drill_http_method");
    assertThat(sql).contains("drill_http_status");
    assertThat(sql).contains(ClickhouseConstants.OTEL_TRACES_TABLE);
    assertThat(sql).doesNotContain(ClickhouseConstants.STACK_TRACE_EVENTS_TABLE);
    assertThat(sql).contains("SessionId IN (SELECT SessionId FROM session_metrics)");
    assertThat(sql).doesNotContain("uniqCombined64(ns.SessionId)");
    assertSharedBinds(spec);
  }

  @Test
  void apiWhenIssueMustPrecedePoor_usesPoorEndTsAgainstFirstEndpointErrorTs() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.api, PARAMS_TEMPORAL_ON);
    String sql = spec.sql();
    assertThat(sql).contains("first_endpoint_error_ts < ta.poor_end_ts");
    assertThat(sql).contains("min(Timestamp) AS first_endpoint_error_ts");
    assertSharedBinds(spec);
  }

  private static void assertSharedBinds(RootCauseQuerySpec spec) {
    assertThat(spec.bindNames())
        .containsExactly("rca_p0", "rca_p1", "rca_p2", "rca_p3", "rca_p4", "rca_p5", "rca_p6");
    assertThat(spec.bindValues().get(0)).isEqualTo("proj-1");
    assertThat(spec.bindValues().get(1)).isEqualTo("checkout");
    assertThat(spec.bindValues().get(2))
        .isEqualTo(START.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL));
    assertThat(spec.bindValues().get(3))
        .isEqualTo(END.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL));
    assertThat(spec.bindValues().get(4)).isEqualTo(RootCauseConfig.DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION);
    assertThat(spec.bindValues().get(5)).isEqualTo(RootCauseConfig.DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION);
    assertThat(spec.bindValues().get(6)).isEqualTo(ErrorAttributionDrillDownQueryBuilder.DRILL_DOWN_CANDIDATE_LIMIT);
  }
}
