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
 * optionally {@code n_control}, ranked by finite RR (then infinite RR, then {@code n_treated}). SQL
 * fragments are defined in {@link ErrorAttributionDrillDownSqlTemplates}; this class wires bind
 * values only.
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
      case crash ->
          buildStack(
              projectId,
              interactionName,
              startInclusive,
              endExclusive,
              ClickhouseConstants.CH_PULSE_TYPE_DEVICE_CRASH,
              false,
              params);
      case anr ->
          buildStack(
              projectId,
              interactionName,
              startInclusive,
              endExclusive,
              ClickhouseConstants.CH_PULSE_TYPE_DEVICE_ANR,
              false,
              params);
      case non_fatal ->
          buildStack(
              projectId,
              interactionName,
              startInclusive,
              endExclusive,
              ClickhouseConstants.CH_PULSE_TYPE_NON_FATAL,
              true,
              params);
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
    String poorExpr = ClickhouseConstants.CH_SPAN_USER_CATEGORY_IS_POOR;

    return acc.toSpec(
        ErrorAttributionDrillDownSqlTemplates.stackOptimizedSql(
            tripleKey,
            params,
            traces,
            stacks,
            interactionType,
            stackPulseType,
            poorExpr,
            p0,
            p1,
            p2,
            p3,
            p4,
            p5,
            p6));
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
    String poorExpr = ClickhouseConstants.CH_SPAN_USER_CATEGORY_IS_POOR;

    String urlExpr = ClickhouseConstants.CH_SPAN_HTTP_URL_EXPR;
    String gqlNameExpr = ClickhouseConstants.CH_SPAN_GRAPHQL_OPERATION_NAME_EXPR;
    String gqlTypeExpr = ClickhouseConstants.CH_SPAN_GRAPHQL_OPERATION_TYPE_EXPR;
    String methodExpr = ClickhouseConstants.CH_SPAN_HTTP_METHOD_EXPR;
    String statusCodeExpr = ClickhouseConstants.CH_SPAN_HTTP_STATUS_CODE_EXPR;

    return acc.toSpec(
        ErrorAttributionDrillDownSqlTemplates.apiOptimizedSql(
            params,
            traces,
            interactionType,
            poorExpr,
            urlExpr,
            gqlNameExpr,
            gqlTypeExpr,
            methodExpr,
            statusCodeExpr,
            p0,
            p1,
            p2,
            p3,
            p4,
            p5,
            p6));
  }
}
