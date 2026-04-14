package org.dreamhorizon.pulseserver.service.errorattribution;

import java.time.Instant;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.interaction.InteractionTelemetryConstants;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;

/**
 * ClickHouse CTE query for Track B Phase 1 error attribution (universe U, trace_agg, stack_agg).
 */
public final class ErrorAttributionQueryBuilder {

  private ErrorAttributionQueryBuilder() {
  }

  /**
   * Builds the diagnostic query with four named binds ({@code rca_p0}…{@code rca_p3}) reused across CTEs.
   */
  public static RootCauseQuerySpec build(
      String projectId, String interactionName, Instant startInclusive, Instant endExclusive) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, projectId == null ? "" : projectId);
    acc.add(p1, interactionName == null ? "" : interactionName);
    String startStr =
        startInclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        endExclusive.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);

    String traces = ClickhouseConstants.OTEL_TRACES_TABLE;
    String stacks = ClickhouseConstants.STACK_TRACE_EVENTS_TABLE;
    String interactionType = InteractionTelemetryConstants.INTERACTION_PULSE_TYPE;

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
            + "maxIf(1, PulseType LIKE 'network.%' AND StatusCode = 'Error') AS t_api "
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
            + "stack_agg AS ( "
            + "SELECT "
            + "SessionId, "
            + "maxIf(1, PulseType = 'device.crash') AS t_crash, "
            + "maxIf(1, PulseType = 'device.anr') AS t_anr, "
            + "maxIf(1, PulseType = 'non_fatal') AS t_nf "
            + "FROM "
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
            + "GROUP BY SessionId "
            + ") "
            + "SELECT "
            + "count() AS n_u, "
            + "sum(ta.is_low) AS n_poor_u, "
            + "sum(ifNull(sa.t_crash, 0)) AS n_treated_crash, "
            + "countIf(ifNull(sa.t_crash, 0) = 0) AS n_control_crash, "
            + "sumIf(ta.is_low, ifNull(sa.t_crash, 0) = 1) AS n_treated_low_crash, "
            + "sumIf(ta.is_low, ifNull(sa.t_crash, 0) = 0) AS n_control_low_crash, "
            + "sum(ifNull(sa.t_anr, 0)) AS n_treated_anr, "
            + "countIf(ifNull(sa.t_anr, 0) = 0) AS n_control_anr, "
            + "sumIf(ta.is_low, ifNull(sa.t_anr, 0) = 1) AS n_treated_low_anr, "
            + "sumIf(ta.is_low, ifNull(sa.t_anr, 0) = 0) AS n_control_low_anr, "
            + "sum(ifNull(sa.t_nf, 0)) AS n_treated_nf, "
            + "countIf(ifNull(sa.t_nf, 0) = 0) AS n_control_nf, "
            + "sumIf(ta.is_low, ifNull(sa.t_nf, 0) = 1) AS n_treated_low_nf, "
            + "sumIf(ta.is_low, ifNull(sa.t_nf, 0) = 0) AS n_control_low_nf, "
            + "sum(ta.t_api) AS n_treated_api, "
            + "countIf(ta.t_api = 0) AS n_control_api, "
            + "sumIf(ta.is_low, ta.t_api = 1) AS n_treated_low_api, "
            + "sumIf(ta.is_low, ta.t_api = 0) AS n_control_low_api "
            + "FROM trace_agg ta "
            + "LEFT JOIN stack_agg sa USING (SessionId)";

    return acc.toSpec(sql);
  }
}
