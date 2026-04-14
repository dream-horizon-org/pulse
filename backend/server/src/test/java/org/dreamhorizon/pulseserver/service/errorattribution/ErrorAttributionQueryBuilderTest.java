package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;
import org.junit.jupiter.api.Test;

class ErrorAttributionQueryBuilderTest {

  @Test
  void shouldBuildCteQueryWithSharedNamedBindsAndExpectedTables() {
    Instant start = Instant.parse("2026-04-01T00:00:00Z");
    Instant end = Instant.parse("2026-04-08T12:00:00Z");
    RootCauseQuerySpec spec =
        ErrorAttributionQueryBuilder.build("proj-1", "checkout", start, end);

    assertThat(spec.bindNames()).containsExactly("rca_p0", "rca_p1", "rca_p2", "rca_p3");
    assertThat(spec.bindValues().get(0)).isEqualTo("proj-1");
    assertThat(spec.bindValues().get(1)).isEqualTo("checkout");
    assertThat(spec.bindValues().get(2))
        .isEqualTo(start.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL));
    assertThat(spec.bindValues().get(3))
        .isEqualTo(end.atOffset(ZoneOffset.UTC).format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL));

    String sql = spec.sql();
    assertThat(sql).startsWith("WITH ");
    assertThat(sql).contains("u_sessions AS");
    assertThat(sql).contains("trace_agg AS");
    assertThat(sql).contains("stack_agg AS");
    assertThat(sql).contains(ClickhouseConstants.OTEL_TRACES_TABLE);
    assertThat(sql).contains(ClickhouseConstants.STACK_TRACE_EVENTS_TABLE);
    assertThat(sql).contains("maxIf(1, PulseType = 'interaction'");
    assertThat(sql).contains("ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor'");
    assertThat(sql).contains(":rca_p0");
    assertThat(sql).contains(":rca_p1");
    assertThat(sql).contains("AS n_u");
    assertThat(sql).contains("AS n_treated_api");
  }
}
