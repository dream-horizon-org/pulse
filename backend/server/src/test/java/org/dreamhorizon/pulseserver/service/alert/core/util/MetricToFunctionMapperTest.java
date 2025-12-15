package org.dreamhorizon.pulseserver.service.alert.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MetricToFunctionMapperTest {

  @Nested
  class TestMapMetricToFunction {

    @Test
    void shouldReturnNullForNullMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction(null);

      assertNull(result);
    }

    @Test
    void shouldReturnNullForEmptyMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("");

      assertNull(result);
    }

    @Test
    void shouldMapApdexMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("APDEX");

      assertNotNull(result);
      assertEquals(Functions.APDEX, result);
    }

    @Test
    void shouldMapLowercaseMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("apdex");

      assertNotNull(result);
      assertEquals(Functions.APDEX, result);
    }

    @Test
    void shouldMapMixedCaseMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("Apdex");

      assertNotNull(result);
      assertEquals(Functions.APDEX, result);
    }

    @Test
    void shouldMapCrashMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("crash");

      assertNotNull(result);
      assertEquals(Functions.CRASH, result);
    }

    @Test
    void shouldMapAnrMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("anr");

      assertNotNull(result);
      assertEquals(Functions.ANR, result);
    }

    @Test
    void shouldMapFrozenFrameMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("frozen_frame");

      assertNotNull(result);
      assertEquals(Functions.FROZEN_FRAME, result);
    }

    @Test
    void shouldMapErrorRateMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("error_rate");

      assertNotNull(result);
      assertEquals(Functions.ERROR_RATE, result);
    }

    @Test
    void shouldMapCrashRateMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("crash_rate");

      assertNotNull(result);
      assertEquals(Functions.CRASH_RATE, result);
    }

    @Test
    void shouldMapAnrRateMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("anr_rate");

      assertNotNull(result);
      assertEquals(Functions.ANR_RATE, result);
    }

    @Test
    void shouldReturnNullForUnknownMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("unknown_metric");

      assertNull(result);
    }

    @Test
    void shouldReturnNullForInvalidMetric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("INVALID_METRIC_NAME_12345");

      assertNull(result);
    }

    @Test
    void shouldMapDurationP99Metric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("duration_p99");

      assertNotNull(result);
      assertEquals(Functions.DURATION_P99, result);
    }

    @Test
    void shouldMapDurationP95Metric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("duration_p95");

      assertNotNull(result);
      assertEquals(Functions.DURATION_P95, result);
    }

    @Test
    void shouldMapDurationP50Metric() {
      Functions result = MetricToFunctionMapper.mapMetricToFunction("duration_p50");

      assertNotNull(result);
      assertEquals(Functions.DURATION_P50, result);
    }

    @Test
    void shouldMapNetworkMetrics() {
      assertEquals(Functions.NET_0, MetricToFunctionMapper.mapMetricToFunction("net_0"));
      assertEquals(Functions.NET_2XX, MetricToFunctionMapper.mapMetricToFunction("net_2xx"));
      assertEquals(Functions.NET_3XX, MetricToFunctionMapper.mapMetricToFunction("net_3xx"));
      assertEquals(Functions.NET_4XX, MetricToFunctionMapper.mapMetricToFunction("net_4xx"));
      assertEquals(Functions.NET_5XX, MetricToFunctionMapper.mapMetricToFunction("net_5xx"));
    }

    @Test
    void shouldMapUserCategoryMetrics() {
      assertEquals(Functions.USER_CATEGORY_EXCELLENT, MetricToFunctionMapper.mapMetricToFunction("user_category_excellent"));
      assertEquals(Functions.USER_CATEGORY_GOOD, MetricToFunctionMapper.mapMetricToFunction("user_category_good"));
      assertEquals(Functions.USER_CATEGORY_AVERAGE, MetricToFunctionMapper.mapMetricToFunction("user_category_average"));
      assertEquals(Functions.USER_CATEGORY_POOR, MetricToFunctionMapper.mapMetricToFunction("user_category_poor"));
    }

    @Test
    void shouldMapAppVitalsMetrics() {
      assertEquals(Functions.APP_VITALS_CRASH_FREE_USERS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("app_vitals_crash_free_users_percentage"));
      assertEquals(Functions.APP_VITALS_CRASH_FREE_SESSIONS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("app_vitals_crash_free_sessions_percentage"));
      assertEquals(Functions.APP_VITALS_CRASH_USERS, 
          MetricToFunctionMapper.mapMetricToFunction("app_vitals_crash_users"));
      assertEquals(Functions.APP_VITALS_CRASH_SESSIONS, 
          MetricToFunctionMapper.mapMetricToFunction("app_vitals_crash_sessions"));
    }
  }

  @Nested
  class TestFunctionsEnum {

    @Test
    void shouldHaveCorrectDisplayNameForApdex() {
      assertEquals("apdex", Functions.APDEX.getDisplayName());
    }

    @Test
    void shouldHaveCorrectDisplayNameForCrash() {
      assertEquals("crash", Functions.CRASH.getDisplayName());
    }

    @Test
    void shouldHaveCorrectDisplayNameForAnr() {
      assertEquals("anr", Functions.ANR.getDisplayName());
    }

    @Test
    void shouldHaveChSelectClause() {
      assertNotNull(Functions.APDEX.getChSelectClause());
      assertNotNull(Functions.CRASH.getChSelectClause());
      assertNotNull(Functions.ANR.getChSelectClause());
    }

    @Test
    void shouldHaveAllEnumValues() {
      Functions[] values = Functions.values();
      assertNotNull(values);
      // Just verify we can enumerate all values
      for (Functions f : values) {
        assertNotNull(f.getDisplayName());
        assertNotNull(f.getChSelectClause());
      }
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(Functions.APDEX, Functions.valueOf("APDEX"));
      assertEquals(Functions.CRASH, Functions.valueOf("CRASH"));
      assertEquals(Functions.ERROR_RATE, Functions.valueOf("ERROR_RATE"));
    }
  }
}

