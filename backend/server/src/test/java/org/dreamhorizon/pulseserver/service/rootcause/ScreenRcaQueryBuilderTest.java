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

class ScreenRcaQueryBuilderTest {

  private static final String PROJECT = "proj-1";
  private static final String SCREEN = "CheckoutScreen";
  private static final Instant START =
      LocalDate.of(2025, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
  private static final Instant END =
      LocalDate.of(2025, 3, 8).atStartOfDay(ZoneOffset.UTC).toInstant();

  @Nested
  class MetricConstants {

    @Test
    void shouldExposeBadClickMetricKeys() {
      assertThat(ScreenRcaQueryBuilder.RAGE_COUNT).isEqualTo("rage_count");
      assertThat(ScreenRcaQueryBuilder.DEAD_COUNT).isEqualTo("dead_count");
      assertThat(ScreenRcaQueryBuilder.APP_CLICK_PULSE_TYPE).isEqualTo("app.click");
    }
  }

  @Nested
  class DimensionExpression {

    @Test
    void shouldMapKnownDimensionsToNormalizedExpression() {
      assertThat(ScreenRcaQueryBuilder.dimensionExpression("Platform"))
          .isEqualTo("ifNull(nullIf(trimBoth(Platform), ''), 'Unknown')");
      assertThat(ScreenRcaQueryBuilder.dimensionExpression("GeoState"))
          .isEqualTo("ifNull(nullIf(trimBoth(GeoState), ''), 'Unknown')");
    }

    @Test
    void shouldRejectUnknownDimension() {
      assertThatThrownBy(() -> ScreenRcaQueryBuilder.dimensionExpression("UnknownDim"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown Screen RCA dimension");
    }

    @Test
    void shouldBuildSelectAliasWrappingExpression() {
      assertThat(ScreenRcaQueryBuilder.dimensionSelectAlias("AppVersion"))
          .isEqualTo(
              "ifNull(nullIf(trimBoth(AppVersion), ''), 'Unknown') AS AppVersion");
    }
  }

  @Nested
  class SqlGeneration {

    @Test
    void shouldIncludeProjectScreenAppClickAndMetricsInBaselineQuery() {
      RootCauseQuerySpec spec =
          ScreenRcaQueryBuilder.buildBaselineQuery(PROJECT, SCREEN, START, END);

      assertThat(spec.sql()).contains("FROM otel.otel_logs");
      assertThat(spec.sql()).contains("PulseType = 'app.click'");
      assertThat(spec.sql()).contains("trimBoth(ScreenName)");
      assertThat(spec.sql()).contains("countIf(Rage)");
      assertThat(spec.sql()).contains("AS " + ScreenRcaQueryBuilder.CLICK_VOLUME);
      assertThat(spec.sql()).contains("AS " + ScreenRcaQueryBuilder.RAGE_COUNT);
      assertThat(spec.sql()).contains("AS " + ScreenRcaQueryBuilder.DEAD_COUNT);
      assertThat(spec.sql()).doesNotContain("tap_count");
      assertThat(spec.sql()).doesNotContain("bad_frustration");
      assertThat(spec.sql()).doesNotContain("GROUP BY");
      assertThat(spec.bindNames()).hasSize(4);
      assertThat(spec.bindValues().get(0)).isEqualTo(PROJECT);
      assertThat(spec.bindValues().get(1)).isEqualTo(SCREEN);
    }

    @Test
    void shouldPassSpecialCharactersViaBindParametersNotInlineSql() {
      RootCauseQuerySpec spec =
          ScreenRcaQueryBuilder.buildBaselineQuery("p'1", "s'cr", START, END);

      assertThat(spec.sql()).doesNotContain("p'1");
      assertThat(spec.sql()).doesNotContain("s'cr");
      assertThat(spec.bindValues().get(0)).isEqualTo("p'1");
      assertThat(spec.bindValues().get(1)).isEqualTo("s'cr");
    }

    @Test
    void shouldBuildBaseWhereWithFourNamedParameters() {
      RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
      String where = ScreenRcaQueryBuilder.baseWhereSql(acc, PROJECT, SCREEN, START, END);

      assertThat(where).contains("ProjectId = :");
      assertThat(where).contains("toDateTime64(:");
      RootCauseQuerySpec spec = acc.toSpec("SELECT 1 WHERE " + where);
      assertThat(spec.bindNames()).hasSize(4);
      assertThat(spec.bindValues()).hasSize(4);
    }

    @Test
    void shouldBuildBadClickByDimensionQueryWithOptionalFilters() {
      RootCauseQuerySpec spec =
          ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
              PROJECT, SCREEN, START, END, "AppVersion", Map.of("Platform", "iOS"));

      assertThat(spec.sql()).contains("GROUP BY AppVersion");
      assertThat(spec.sql()).contains("affected_user_count");
      assertThat(spec.sql()).doesNotContain("bad_frustration");
      assertThat(spec.sql()).contains("(Platform) = :");
      assertThat(spec.bindValues()).hasSize(5);
      assertThat(spec.bindValues().get(4)).isEqualTo("iOS");
    }

    @Test
    void shouldBuildAnrSpecificIssuesQueryUsingThreadNameFromLogAttributes() {
      RootCauseQuerySpec spec =
          ScreenRcaQueryBuilder.buildAnrSpecificIssuesQuery(
              PROJECT, SCREEN, START, END, "Platform", "Android");

      assertThat(spec.sql()).contains("ExceptionMessage AS issue");
      assertThat(spec.sql()).contains("LogAttributes['thread.name']");
      assertThat(spec.sql()).contains("AS thread");
      assertThat(spec.sql()).doesNotContain("Title AS thread");
      assertThat(spec.sql()).contains("PulseType = 'device.anr'");
      assertThat(spec.sql()).contains("GROUP BY group_id, issue, thread");
      assertThat(spec.bindValues()).hasSize(5);
      assertThat(spec.bindValues().get(4)).isEqualTo("Android");
    }
  }
}
