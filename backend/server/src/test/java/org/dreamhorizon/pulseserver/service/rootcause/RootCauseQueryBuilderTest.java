package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RootCauseQueryBuilderTest {

  @Nested
  class MetricsRegistry {

    @Test
    void shouldExposeAllMetricKeysAndExpressions() {
      Map<String, String> metrics = RootCauseMetricsRegistry.getMetricExpressions();

      assertThat(metrics).containsKeys(
          RootCauseMetricsRegistry.VOLUME,
          RootCauseMetricsRegistry.APDEX,
          RootCauseMetricsRegistry.ERROR_RATE,
          RootCauseMetricsRegistry.POOR_USER_PCT,
          RootCauseMetricsRegistry.DURATION_P50,
          RootCauseMetricsRegistry.DURATION_P95,
          RootCauseMetricsRegistry.CRASH_RATE,
          RootCauseMetricsRegistry.ANR_RATE,
          RootCauseMetricsRegistry.FROZEN_FRAME_RATE,
          RootCauseMetricsRegistry.SLOW_FRAME_RATE);

      assertThat(metrics.get(RootCauseMetricsRegistry.VOLUME)).isEqualTo("count()");
      assertThat(metrics.values()).noneMatch(String::isBlank);
    }

    @Test
    void shouldReturnProblematicCountSqlFragment() {
      String expr = RootCauseMetricsRegistry.getProblematicCountExpression();

      assertThat(expr).isNotBlank();
    }
  }

  private static final String PROJECT = "proj-1";
  private static final String INTERACTION = "checkout";
  private static final Instant START = LocalDate.of(2025, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
  private static final Instant END = LocalDate.of(2025, 3, 8).atStartOfDay(ZoneOffset.UTC).toInstant();

  @Nested
  class WindowBounds {

    @Test
    void shouldComputeInclusiveStartAndExclusiveEnd() {
      LocalDate endDate = LocalDate.of(2025, 6, 10);
      RootCauseQueryBuilder.Window window = new RootCauseQueryBuilder.Window(endDate, 3);

      Instant expectedEnd = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant expectedStart = endDate.minusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant();

      assertThat(window.endExclusive).isEqualTo(expectedEnd);
      assertThat(window.startInclusive).isEqualTo(expectedStart);
    }
  }

  @Nested
  class SqlGeneration {

    @Test
    void shouldIncludeProjectInteractionAndTableInBaselineQuery() {
      RootCauseQuerySpec spec =
          RootCauseQueryBuilder.buildBaselineQuery(PROJECT, INTERACTION, START, END);

      assertThat(spec.sql()).contains("FROM otel.otel_traces");
      assertThat(spec.sql()).contains("ProjectId = :rca_p0");
      assertThat(spec.sql()).contains("SpanName = :rca_p1");
      assertThat(spec.sql()).contains("PulseType = 'interaction'");
      assertThat(spec.sql()).contains("problematic_count");
      assertThat(spec.sql()).doesNotContain("GROUP BY");
      assertThat(spec.bindNames()).hasSize(4);
      assertThat(spec.bindValues()).hasSize(4);
      assertThat(spec.bindValues().get(0)).isEqualTo(PROJECT);
      assertThat(spec.bindValues().get(1)).isEqualTo(INTERACTION);
    }

    @Test
    void shouldPassSpecialCharactersViaBindParametersNotInlineSql() {
      RootCauseQuerySpec spec =
          RootCauseQueryBuilder.buildBaselineQuery("p'1", "i'n", START, END);

      assertThat(spec.sql()).doesNotContain("p'1");
      assertThat(spec.sql()).doesNotContain("i'n");
      assertThat(spec.bindValues().get(0)).isEqualTo("p'1");
      assertThat(spec.bindValues().get(1)).isEqualTo("i'n");
    }

    @Test
    void shouldBuildBaseWhereWithFourNamedParameters() {
      RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
      String where =
          RootCauseQueryBuilder.baseWhereSql(acc, PROJECT, INTERACTION, START, END);

      assertThat(where).contains("ProjectId = :rca_p0");
      assertThat(where).contains("SpanName = :rca_p1");
      assertThat(where).contains("toDateTime64(:rca_p2, 9, 'UTC')");
      RootCauseQuerySpec spec = acc.toSpec("SELECT 1 WHERE " + where);
      assertThat(spec.bindNames()).hasSize(4);
      assertThat(spec.bindValues()).hasSize(4);
    }

    @Test
    void shouldBuildSegmentQueryWithGroupByAndFilters() {
      RootCauseQuerySpec spec =
          RootCauseQueryBuilder.buildSegmentQuery(
              PROJECT,
              INTERACTION,
              START,
              END,
              List.of("Platform", "OsVersion"),
              Map.of("Platform", "Android"));

      assertThat(spec.sql()).contains("GROUP BY Platform, OsVersion");
      assertThat(spec.sql()).contains("Platform = :rca_p4");
      assertThat(spec.sql()).contains("FROM otel.otel_traces");
      assertThat(spec.bindValues()).hasSize(5);
      assertThat(spec.bindValues().get(4)).isEqualTo("Android");
    }

    @Test
    void shouldRejectEmptyDimensionListForSegmentQuery() {
      assertThatThrownBy(
              () ->
                  RootCauseQueryBuilder.buildSegmentQuery(
                      PROJECT, INTERACTION, START, END, List.of(), Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("non-empty");
    }

    @Test
    void shouldBuildProblematicCountByDimensionQuery() {
      RootCauseQuerySpec spec =
          RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
              PROJECT,
              INTERACTION,
              START,
              END,
              "Platform",
              Map.of("OsVersion", "14"));

      assertThat(spec.sql()).contains("GROUP BY Platform");
      assertThat(spec.sql()).contains("OsVersion = :rca_p4");
      assertThat(spec.sql()).contains("AS problematic_count");
      assertThat(spec.bindValues()).hasSize(5);
      assertThat(spec.bindValues().get(4)).isEqualTo("14");
    }
  }
}
