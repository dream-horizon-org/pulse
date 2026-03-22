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
      String sql =
          RootCauseQueryBuilder.buildBaselineQuery(PROJECT, INTERACTION, START, END);

      assertThat(sql).contains("FROM otel.otel_traces");
      assertThat(sql).contains("ProjectId = 'proj-1'");
      assertThat(sql).contains("SpanName = 'checkout'");
      assertThat(sql).contains("PulseType = 'interaction'");
      assertThat(sql).contains("problematic_count");
      assertThat(sql).doesNotContain("GROUP BY");
    }

    @Test
    void shouldEscapeSingleQuotesInIdentifiers() {
      String sql =
          RootCauseQueryBuilder.baseWhere(
              "p'1", "i'n", START, END);

      assertThat(sql).contains("p\\'1");
      assertThat(sql).contains("i\\'n");
    }

    @Test
    void shouldBuildSegmentQueryWithGroupByAndFilters() {
      String sql =
          RootCauseQueryBuilder.buildSegmentQuery(
              PROJECT,
              INTERACTION,
              START,
              END,
              List.of("Platform", "OsVersion"),
              Map.of("Platform", "Android"));

      assertThat(sql).contains("GROUP BY Platform, OsVersion");
      assertThat(sql).contains("Platform = 'Android'");
      assertThat(sql).contains("FROM otel.otel_traces");
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
      String sql =
          RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
              PROJECT,
              INTERACTION,
              START,
              END,
              "Platform",
              Map.of("OsVersion", "14"));

      assertThat(sql).contains("GROUP BY Platform");
      assertThat(sql).contains("OsVersion = '14'");
      assertThat(sql).contains("AS problematic_count");
    }
  }
}
