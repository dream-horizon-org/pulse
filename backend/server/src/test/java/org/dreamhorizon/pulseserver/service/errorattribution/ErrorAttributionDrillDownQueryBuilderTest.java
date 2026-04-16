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
  void crashUsesModeAStackSessionsAndKeyStats() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.crash, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("stack_sessions AS");
    assertThat(sql).contains("key_stats AS");
    assertThat(sql).contains("universe AS");
    assertThat(sql).contains("poor_tot AS");
    assertThat(sql).contains(" AS poor_ts ");
    assertThat(sql).doesNotContain("eligible_stack_groups");
    assertThat(sql).doesNotContain("poor_sessions AS");
    assertThat(sql).contains("SELECT SessionId, GroupId, Title FROM ");
    assertThat(sql).contains("PulseType = 'device.crash'");
    assertThat(sql).contains("LIMIT toInt64(:");
    assertThat(sql).doesNotContain("first_issue_ts < ta.poor_ts");
    assertSharedBinds(spec);
  }

  @Test
  void crashWhenIssueMustPrecedePoor_includesFirstIssueTsAndPoorWindowGuard() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.crash, PARAMS_TEMPORAL_ON);
    String sql = spec.sql();
    assertThat(sql).contains("min(Timestamp) AS first_issue_ts");
    assertThat(sql).contains("first_issue_ts < ta.poor_ts");
    assertThat(sql).contains("ta.poor_ts >= toDateTime64(:");
    assertThat(sql).contains("ta.poor_ts < toDateTime64(:");
    assertThat(sql).doesNotContain("poor_ts IS NOT NULL");
    assertSharedBinds(spec);
  }

  @Test
  void nonFatalUsesTripleInStackSessions() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.non_fatal, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("SELECT SessionId, GroupId, Title, ExceptionType FROM ");
    assertThat(sql).contains("PulseType = 'non_fatal'");
    assertSharedBinds(spec);
  }

  @Test
  void apiUsesNetworkSessionsAndOtelTableOnly() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.api, PARAMS_OFF);
    String sql = spec.sql();
    assertThat(sql).contains("network_sessions AS");
    assertThat(sql).contains("SpanAttributes['http.url']");
    assertThat(sql).contains(ClickhouseConstants.OTEL_TRACES_TABLE);
    assertThat(sql).doesNotContain(ClickhouseConstants.STACK_TRACE_EVENTS_TABLE);
    assertThat(sql).contains(" AS poor_ts ");
    assertSharedBinds(spec);
  }

  @Test
  void apiWhenIssueMustPrecedePoor_joinsTraceAggTimestampsForNTreatedLow() {
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            "proj-1", "checkout", START, END, ErrorAttributionDrillDownSignal.api, PARAMS_TEMPORAL_ON);
    String sql = spec.sql();
    assertThat(sql).contains("first_endpoint_error_ts < ta.poor_ts");
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
