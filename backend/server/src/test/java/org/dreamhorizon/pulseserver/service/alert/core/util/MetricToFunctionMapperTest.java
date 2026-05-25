package org.dreamhorizon.pulseserver.service.alert.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
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
      assertEquals(Functions.CRASH_FREE_USERS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("crash_free_users_percentage"));
      assertEquals(Functions.CRASH_FREE_SESSIONS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("crash_free_sessions_percentage"));
      assertEquals(Functions.CRASH_USERS, 
          MetricToFunctionMapper.mapMetricToFunction("crash_users"));
      assertEquals(Functions.CRASH_SESSIONS, 
          MetricToFunctionMapper.mapMetricToFunction("crash_sessions"));
      assertEquals(Functions.ALL_USERS, 
          MetricToFunctionMapper.mapMetricToFunction("all_users"));
      assertEquals(Functions.ALL_SESSIONS, 
          MetricToFunctionMapper.mapMetricToFunction("all_sessions"));
      assertEquals(Functions.ANR_FREE_USERS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("anr_free_users_percentage"));
      assertEquals(Functions.ANR_FREE_SESSIONS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("anr_free_sessions_percentage"));
      assertEquals(Functions.ANR_USERS, 
          MetricToFunctionMapper.mapMetricToFunction("anr_users"));
      assertEquals(Functions.ANR_SESSIONS, 
          MetricToFunctionMapper.mapMetricToFunction("anr_sessions"));
      assertEquals(Functions.NON_FATAL_FREE_USERS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("non_fatal_free_users_percentage"));
      assertEquals(Functions.NON_FATAL_FREE_SESSIONS_PERCENTAGE, 
          MetricToFunctionMapper.mapMetricToFunction("non_fatal_free_sessions_percentage"));
      assertEquals(Functions.NON_FATAL_USERS, 
          MetricToFunctionMapper.mapMetricToFunction("non_fatal_users"));
      assertEquals(Functions.NON_FATAL_SESSIONS, 
          MetricToFunctionMapper.mapMetricToFunction("non_fatal_sessions"));
    }
  }

  @Nested
  class GetDataTypeForMetricTests {

    @Test
    void shouldUseTracesForAppVitalsAllUsersAndAllSessions() {
      assertEquals(QueryRequest.DataType.TRACES,
          MetricToFunctionMapper.getDataTypeForMetric("ALL_USERS", "APP_VITALS"));
      assertEquals(QueryRequest.DataType.TRACES,
          MetricToFunctionMapper.getDataTypeForMetric("ALL_SESSIONS", "APP_VITALS"));
      assertEquals(QueryRequest.DataType.TRACES,
          MetricToFunctionMapper.getDataTypeForMetric("all_users", "app_vitals"));
    }

    @Test
    void shouldUseTracesForNullOrEmptyMetric() {
      assertEquals(QueryRequest.DataType.TRACES, MetricToFunctionMapper.getDataTypeForMetric(null, "SCREEN"));
      assertEquals(QueryRequest.DataType.TRACES, MetricToFunctionMapper.getDataTypeForMetric("", "SCREEN"));
      assertEquals(QueryRequest.DataType.TRACES, MetricToFunctionMapper.getDataTypeForMetric("APDEX"));
    }

    @Test
    void shouldUseExceptionsForCrashAndAnrMetrics() {
      assertEquals(QueryRequest.DataType.EXCEPTIONS,
          MetricToFunctionMapper.getDataTypeForMetric("CRASH_USERS", "APP_VITALS"));
      assertEquals(QueryRequest.DataType.EXCEPTIONS,
          MetricToFunctionMapper.getDataTypeForMetric("ANR_SESSIONS", "SCREEN"));
      assertEquals(QueryRequest.DataType.EXCEPTIONS,
          MetricToFunctionMapper.getDataTypeForMetric("NON_FATAL_USERS", "APP_VITALS"));
    }

    @Test
    void shouldReturnNullDataTypeForCompositeMetrics() {
      assertNull(MetricToFunctionMapper.getDataTypeForMetric("CRASH_FREE_USERS_PERCENTAGE", "APP_VITALS"));
      assertNull(MetricToFunctionMapper.getDataTypeForMetric("ANR_FREE_SESSIONS_PERCENTAGE", "SCREEN"));
    }

    @Test
    void shouldUseTracesForNonExceptionScreenMetrics() {
      assertEquals(QueryRequest.DataType.TRACES,
          MetricToFunctionMapper.getDataTypeForMetric("LOAD_TIME", "SCREEN"));
    }
  }

  @Nested
  class IsCompositeMetricTests {

    @Test
    void shouldDetectCompositeMetrics() {
      assertTrue(MetricToFunctionMapper.isCompositeMetric("CRASH_FREE_USERS_PERCENTAGE"));
      assertTrue(MetricToFunctionMapper.isCompositeMetric("non_fatal_free_sessions_percentage"));
    }

    @Test
    void shouldRejectNullEmptyAndSimpleMetrics() {
      assertFalse(MetricToFunctionMapper.isCompositeMetric(null));
      assertFalse(MetricToFunctionMapper.isCompositeMetric(""));
      assertFalse(MetricToFunctionMapper.isCompositeMetric("ALL_USERS"));
    }
  }

  @Nested
  class GetCompositeMetricComponentsTests {

    @Test
    void shouldUseTracesDenominatorForAppVitalsCompositeMetrics() {
      MetricToFunctionMapper.CompositeMetricComponents components =
          MetricToFunctionMapper.getCompositeMetricComponents("CRASH_FREE_USERS_PERCENTAGE", "APP_VITALS");
      assertNotNull(components);
      assertEquals("ALL_USERS", components.tracesMetric);
      assertEquals("CRASH_USERS", components.exceptionsMetric);
      assertEquals(QueryRequest.DataType.TRACES, components.totalMetricDataType);
    }

    @Test
    void shouldMapAllCompositeMetricVariants() {
      assertNotNull(MetricToFunctionMapper.getCompositeMetricComponents("CRASH_FREE_SESSIONS_PERCENTAGE", "APP_VITALS"));
      assertNotNull(MetricToFunctionMapper.getCompositeMetricComponents("ANR_FREE_USERS_PERCENTAGE", "APP_VITALS"));
      assertNotNull(MetricToFunctionMapper.getCompositeMetricComponents("ANR_FREE_SESSIONS_PERCENTAGE", "APP_VITALS"));
      assertNotNull(MetricToFunctionMapper.getCompositeMetricComponents("NON_FATAL_FREE_USERS_PERCENTAGE", "APP_VITALS"));
      assertNotNull(MetricToFunctionMapper.getCompositeMetricComponents("NON_FATAL_FREE_SESSIONS_PERCENTAGE", "APP_VITALS"));
    }

    @Test
    void shouldReturnNullForUnknownOrEmptyMetric() {
      assertNull(MetricToFunctionMapper.getCompositeMetricComponents(null, "APP_VITALS"));
      assertNull(MetricToFunctionMapper.getCompositeMetricComponents("", "APP_VITALS"));
      assertNull(MetricToFunctionMapper.getCompositeMetricComponents("LOAD_TIME", "SCREEN"));
    }

    @Test
    void shouldUseTwoArgConstructorOverload() {
      MetricToFunctionMapper.CompositeMetricComponents components =
          MetricToFunctionMapper.getCompositeMetricComponents("CRASH_FREE_USERS_PERCENTAGE");
      assertNotNull(components);
      assertEquals(QueryRequest.DataType.TRACES, components.totalMetricDataType);
    }
  }

  @Nested
  class MapMetricToFunctionNetworkApiTests {

    @Test
    void shouldMapNetworkApiMetricsToPulseTypeVariants() {
      assertEquals(Functions.NET_4XX_BY_PULSE_TYPE,
          MetricToFunctionMapper.mapMetricToFunction("NET_4XX", "NETWORK_API"));
      assertEquals(Functions.NET_COUNT_BY_PULSE_TYPE,
          MetricToFunctionMapper.mapMetricToFunction("net_count", "NETWORK_API"));
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

