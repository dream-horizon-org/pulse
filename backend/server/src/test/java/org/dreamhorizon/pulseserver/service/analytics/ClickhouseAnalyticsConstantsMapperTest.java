package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelFilterOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickhouseAnalyticsConstantsMapperTest {

  @Nested
  class FieldMapping {

    @Test
    void shouldMapOsName() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.EQ).value(List.of("Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['os.name']");
    }

    @Test
    void shouldMapOsVersion() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_VERSION").operator(FunnelFilterOperator.EQ).value(List.of("14")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['os.version']");
    }

    @Test
    void shouldMapAppBuildName() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("APP_BUILD_NAME").operator(FunnelFilterOperator.EQ).value(List.of("1.0.0")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['app.build_name']");
    }

    @Test
    void shouldMapAppBuildId() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("APP_BUILD_ID").operator(FunnelFilterOperator.EQ).value(List.of("42")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['app.build_id']");
    }

    @Test
    void shouldMapDeviceManufacturer() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("DEVICE_MANUFACTURER").operator(FunnelFilterOperator.EQ).value(List.of("Samsung")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['device.manufacturer']");
    }

    @Test
    void shouldMapDeviceModelId() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("DEVICE_MODEL_ID").operator(FunnelFilterOperator.EQ).value(List.of("SM-G998")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['device.model.identifier']");
    }

    @Test
    void shouldMapAndroidOsApiLevel() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("ANDROID_OS_API_LEVEL").operator(FunnelFilterOperator.EQ).value(List.of("33")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND ResourceAttributes['android.os.api_level']");
    }

    @Test
    void shouldMapScreenName() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("SCREEN_NAME").operator(FunnelFilterOperator.EQ).value(List.of("HomeScreen")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND LogAttributes['screen.name']");
    }

    @Test
    void shouldMapPulseAppState() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("PULSE_APP_STATE").operator(FunnelFilterOperator.EQ).value(List.of("foreground")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND LogAttributes['pulse.app_state']");
    }

    @Test
    void shouldFallbackToLogAttributesForUnknownField() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("CUSTOM_ATTR").operator(FunnelFilterOperator.EQ).value(List.of("val")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .startsWith("AND LogAttributes['custom_attr']");
    }

    @Test
    void shouldBeCaseInsensitiveForKnownFields() {
      FunnelAttributeFilter lower = FunnelAttributeFilter.builder()
          .field("os_name").operator(FunnelFilterOperator.EQ).value(List.of("iOS")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(lower))
          .startsWith("AND ResourceAttributes['os.name']");
    }
  }

  @Nested
  class OperatorMapping {

    @Test
    void shouldProduceEqClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.EQ).value(List.of("iOS")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND ResourceAttributes['os.name'] = 'iOS'");
    }

    @Test
    void shouldProduceNeClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.NE).value(List.of("Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND ResourceAttributes['os.name'] != 'Android'");
    }

    @Test
    void shouldProduceInClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.IN).value(List.of("iOS", "Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND ResourceAttributes['os.name'] IN ('iOS', 'Android')");
    }

    @Test
    void shouldProduceNotInClause() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("OS_NAME").operator(FunnelFilterOperator.NOT_IN).value(List.of("iOS", "Android")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND ResourceAttributes['os.name'] NOT IN ('iOS', 'Android')");
    }

    @Test
    void shouldEscapeSingleQuotesInValue() {
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("SCREEN_NAME").operator(FunnelFilterOperator.EQ).value(List.of("O'Brien")).build();
      assertThat(ClickhouseAnalyticsConstantsMapper.toSqlClause(filter))
          .isEqualTo("AND LogAttributes['screen.name'] = 'O\\'Brien'");
    }
  }
}
