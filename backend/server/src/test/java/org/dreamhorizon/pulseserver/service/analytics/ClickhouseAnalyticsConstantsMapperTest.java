package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelFilterOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickhouseAnalyticsConstantsMapperTest {

  @Nested
  class FieldMapping {

    @Test
    void shouldMapOsNameToPlatform() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.EQ).value(List.of("Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND Platform");
    }

    @Test
    void shouldMapOsVersion() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_VERSION").operator(FunnelFilterOperator.EQ).value(List.of("14")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND OsVersion");
    }

    @Test
    void shouldMapAppBuildNameToAppVersion() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("APP_BUILD_NAME").operator(FunnelFilterOperator.EQ).value(List.of("1.0.0")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND AppVersion");
    }

    @Test
    void shouldRejectUnknownField() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("SCREEN_NAME").operator(FunnelFilterOperator.EQ).value(List.of("x")).build();
      assertThatThrownBy(() -> ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported filter field for analytics");
    }

    @Test
    void shouldRejectBlankField() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("  ").operator(FunnelFilterOperator.EQ).value(List.of("x")).build();
      assertThatThrownBy(() -> ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeCaseInsensitiveForKnownFields() {
      FunnelAttributeFilter lower = FunnelAttributeFilter.builder()
          .field("os_name").operator(FunnelFilterOperator.EQ).value(List.of("iOS")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(lower))
          .startsWith("AND Platform");
    }
  }

  @Nested
  class OperatorMapping {

    @Test
    void shouldProduceEqClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.EQ).value(List.of("iOS")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND Platform = 'iOS'");
    }

    @Test
    void shouldProduceNeClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.NE).value(List.of("Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND Platform != 'Android'");
    }

    @Test
    void shouldProduceInClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.IN).value(List.of("iOS", "Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND Platform IN ('iOS', 'Android')");
    }

    @Test
    void shouldProduceNotInClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.NOT_IN).value(List.of("iOS", "Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND Platform NOT IN ('iOS', 'Android')");
    }

    @Test
    void shouldEscapeSingleQuotesInValue() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.EQ).value(List.of("O'Brien")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND Platform = 'O\\'Brien'");
    }
  }
}
