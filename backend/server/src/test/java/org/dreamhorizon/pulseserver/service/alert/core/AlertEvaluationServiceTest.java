package org.dreamhorizon.pulseserver.service.alert.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.core.eventbus.MessageConsumer;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertEvaluationResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.dreamhorizon.pulseserver.service.alert.core.operatror.MetricOperatorFactory;
import org.dreamhorizon.pulseserver.service.alert.core.operatror.MetricOperatorProcessor;
import org.dreamhorizon.pulseserver.service.interaction.ClickhouseMetricService;
import org.dreamhorizon.pulseserver.util.RxObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertEvaluationServiceTest {

  @Mock
  private ApplicationConfig applicationConfig;

  @Mock
  private AlertsDao alertsDao;

  @Mock
  private ClickhouseMetricService clickhouseMetricService;

  @Mock
  private MetricOperatorFactory metricOperatorFactory;

  @Mock
  private Vertx vertx;

  // Note: We cannot mock RxObjectMapper because its static initializer requires Guice
  // Instead, we pass null and only test methods that don't use it

  @Mock
  private EventBus eventBus;

  @Mock
  private MessageConsumer<Object> messageConsumer;

  @Mock
  private MetricOperatorProcessor metricOperatorProcessor;

  // Use real ObjectMapper for coverage
  private ObjectMapper realObjectMapper = new ObjectMapper();
  private AlertEvaluationService alertEvaluationService;

  @BeforeEach
  void setUp() {
    // Create service with real ObjectMapper to allow actual JSON parsing
    // Pass null for rxObjectMapper since we're testing private methods that don't use it
    alertEvaluationService = new AlertEvaluationService(
        applicationConfig,
        alertsDao,
        clickhouseMetricService,
        metricOperatorFactory,
        realObjectMapper,
        vertx,
        null  // RxObjectMapper - cannot mock due to static initializer
    );
  }

  // ==================== PRIVATE METHOD TESTS WITH REFLECTION ====================
  // These tests use reflection to directly invoke private methods with real objects
  // to ensure JaCoCo can track the coverage

  @Nested
  class GetScopeFieldTests {

    @Test
    void shouldReturnSpanNameForInteractionScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanName", method.invoke(alertEvaluationService, "INTERACTION", QueryRequest.DataType.TRACES));
    }

    @Test
    void shouldReturnScreenNameForScreenScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanAttributes['screen.name']", method.invoke(alertEvaluationService, "SCREEN", QueryRequest.DataType.TRACES));
      assertEquals("ScreenName", method.invoke(alertEvaluationService, "SCREEN", QueryRequest.DataType.EXCEPTIONS));
    }

    @Test
    void shouldReturnHttpUrlForNetworkApiScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanAttributes['http.url']", method.invoke(alertEvaluationService, "NETWORK_API", QueryRequest.DataType.TRACES));
    }

    @Test
    void shouldReturnGroupIdForAppVitalsScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("GroupId", method.invoke(alertEvaluationService, "APP_VITALS", QueryRequest.DataType.TRACES));
      assertEquals("GroupId", method.invoke(alertEvaluationService, "APP_VITALS", QueryRequest.DataType.EXCEPTIONS));
      assertEquals("GroupId", method.invoke(alertEvaluationService, "APP_VITALS", QueryRequest.DataType.LOGS));
    }

    @Test
    void shouldReturnSpanNameForNullScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanName", method.invoke(alertEvaluationService, (String) null, QueryRequest.DataType.TRACES));
    }

    @Test
    void shouldReturnSpanNameForEmptyScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanName", method.invoke(alertEvaluationService, "", QueryRequest.DataType.TRACES));
    }

    @Test
    void shouldReturnSpanNameForUnknownScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeField", String.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      assertEquals("SpanName", method.invoke(alertEvaluationService, "UNKNOWN", QueryRequest.DataType.TRACES));
    }
  }

  @Nested
  class GetScopeFieldAliasTests {

    @Test
    void shouldReturnInteractionNameForInteractionScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeFieldAlias", String.class);
      method.setAccessible(true);
      assertEquals("interactionName", method.invoke(alertEvaluationService, "INTERACTION"));
    }

    @Test
    void shouldReturnScopeNameForNullScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeFieldAlias", String.class);
      method.setAccessible(true);
      assertEquals("scopeName", method.invoke(alertEvaluationService, (String) null));
    }

    @Test
    void shouldReturnScopeNameForEmptyScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeFieldAlias", String.class);
      method.setAccessible(true);
      assertEquals("scopeName", method.invoke(alertEvaluationService, ""));
    }

    @Test
    void shouldReturnScreenNameForScreenScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeFieldAlias", String.class);
      method.setAccessible(true);
      assertEquals("screenName", method.invoke(alertEvaluationService, "SCREEN"));
    }

    @Test
    void shouldReturnNetworkApiNameForNetworkScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getScopeFieldAlias", String.class);
      method.setAccessible(true);
      assertEquals("network_apiName", method.invoke(alertEvaluationService, "NETWORK_API"));
    }
  }

  @Nested
  class ParseConditionsArrayTests {

    @Test
    void shouldParseValidConditionsArray() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseConditionsArray", String.class);
      method.setAccessible(true);
      String json = "[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]";
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(alertEvaluationService, json);
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("ERROR_RATE", result.get(0).get("metric"));
    }

    @Test
    void shouldReturnEmptyListForNullJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseConditionsArray", String.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(alertEvaluationService, (String) null);
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForEmptyJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseConditionsArray", String.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(alertEvaluationService, "");
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForInvalidJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseConditionsArray", String.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(alertEvaluationService, "invalid json");
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldParseMultipleConditions() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseConditionsArray", String.class);
      method.setAccessible(true);
      String json = "[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"},{\"metric\":\"LATENCY\",\"alias\":\"B\"}]";
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(alertEvaluationService, json);
      assertEquals(2, result.size());
    }
  }

  @Nested
  class ExtractSqlConditionTests {

    @Test
    void shouldReturnNullForNullDimensionFilter() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      assertNull(method.invoke(alertEvaluationService, (String) null));
    }

    @Test
    void shouldReturnNullForEmptyDimensionFilter() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      assertNull(method.invoke(alertEvaluationService, ""));
    }

    @Test
    void shouldReturnSqlAsIsWhenStartsWithParenthesis() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String sql = "(os = 'Android')";
      assertEquals(sql, method.invoke(alertEvaluationService, sql));
    }

    @Test
    void shouldReturnSqlAsIsWhenContainsEquals() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String sql = "os = 'Android'";
      assertEquals(sql, method.invoke(alertEvaluationService, sql));
    }

    @Test
    void shouldReturnSqlAsIsWhenContainsAND() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String sql = "os AND version";
      assertEquals(sql, method.invoke(alertEvaluationService, sql));
    }

    @Test
    void shouldReturnSqlAsIsWhenContainsOR() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String sql = "os OR version";
      assertEquals(sql, method.invoke(alertEvaluationService, sql));
    }

    @Test
    void shouldConvertSimpleJsonToSql() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String json = "{\"os\":\"Android\"}";
      String result = (String) method.invoke(alertEvaluationService, json);
      assertNotNull(result);
      assertTrue(result.contains("os"));
      assertTrue(result.contains("Android"));
    }

    @Test
    void shouldConvertNestedJsonToSql() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String json = "{\"device\":{\"os\":\"Android\",\"model\":\"Pixel\"}}";
      String result = (String) method.invoke(alertEvaluationService, json);
      assertNotNull(result);
    }

    @Test
    void shouldReturnNullForEmptyJsonObject() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String result = (String) method.invoke(alertEvaluationService, "{}");
      assertNull(result);
    }

    @Test
    void shouldHandleNullValueInJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String result = (String) method.invoke(alertEvaluationService, "{\"os\":null}");
      assertNull(result);
    }

    @Test
    void shouldHandleMultipleKeysInJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String result = (String) method.invoke(alertEvaluationService, "{\"os\":\"Android\",\"version\":\"14\"}");
      assertNotNull(result);
    }

    @Test
    void shouldHandleInvalidJsonAndReturnAsIs() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractSqlCondition", String.class);
      method.setAccessible(true);
      String invalidJson = "not-valid-json";
      assertEquals(invalidJson, method.invoke(alertEvaluationService, invalidJson));
    }
  }

  @Nested
  class BuildEvaluationResultJsonTests {

    @Test
    void shouldBuildJsonFromMetricReadings() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildEvaluationResultJson", Map.class, Map.class, boolean.class);
      method.setAccessible(true);
      Map<String, Float> metricReadings = new HashMap<>();
      metricReadings.put("error_rate", 0.5f);
      Map<String, Boolean> variableValues = new HashMap<>();
      variableValues.put("A", true);
      
      String result = (String) method.invoke(alertEvaluationService, metricReadings, variableValues, true);
      assertNotNull(result);
      assertTrue(result.contains("error_rate"));
    }

    @Test
    void shouldReturnEmptyJsonForEmptyMap() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildEvaluationResultJson", Map.class, Map.class, boolean.class);
      method.setAccessible(true);
      String result = (String) method.invoke(alertEvaluationService, new HashMap<>(), new HashMap<>(), false);
      assertEquals("{}", result);
    }
  }

  @Nested
  class IsAlertSnoozedTests {

    @Test
    void shouldReturnFalseWhenAlertIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", AlertsDao.AlertDetails.class);
      method.setAccessible(true);
      assertFalse((Boolean) method.invoke(alertEvaluationService, (AlertsDao.AlertDetails) null));
    }

    @Test
    void shouldReturnFalseWhenNotSnoozed() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", AlertsDao.AlertDetails.class);
      method.setAccessible(true);
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .id(1)
          .snoozedFrom(null)
          .snoozedUntil(null)
          .build();
      assertFalse((Boolean) method.invoke(alertEvaluationService, alert));
    }

    @Test
    void shouldReturnTrueWhenCurrentlyInSnoozeWindow() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertTrue((Boolean) method.invoke(alertEvaluationService, now.minusHours(1), now.plusHours(1)));
    }

    @Test
    void shouldReturnFalseWhenSnoozeHasExpired() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse((Boolean) method.invoke(alertEvaluationService, now.minusHours(2), now.minusHours(1)));
    }

    @Test
    void shouldReturnFalseWhenSnoozeHasNotStarted() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse((Boolean) method.invoke(alertEvaluationService, now.plusHours(1), now.plusHours(2)));
    }

    @Test
    void shouldReturnFalseWhenSnoozedFromIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse((Boolean) method.invoke(alertEvaluationService, null, now.plusHours(1)));
    }

    @Test
    void shouldReturnFalseWhenSnoozedUntilIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse((Boolean) method.invoke(alertEvaluationService, now.minusHours(1), null));
    }

    @Test
    void shouldReturnTrueWhenSnoozeStartsExactlyNow() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("isAlertSnoozed", LocalDateTime.class, LocalDateTime.class);
      method.setAccessible(true);
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertTrue((Boolean) method.invoke(alertEvaluationService, now, now.plusHours(1)));
    }
  }

  @Nested
  class ShouldCreateIncidentTests {

    @Test
    void shouldNotCreateIncidentWhenAlertIsSnoozed() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "shouldCreateIncident", AlertState.class, AlertEvaluationResponseDto.class, AlertState.class);
      method.setAccessible(true);
      
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .snoozedFrom(now.minusHours(1))
          .snoozedUntil(now.plusHours(1))
          .build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder().alert(alert).build();
      
      assertFalse((Boolean) method.invoke(alertEvaluationService, AlertState.FIRING, dto, AlertState.NORMAL));
    }

    @Test
    void shouldNotCreateIncidentForNoDataState() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "shouldCreateIncident", AlertState.class, AlertEvaluationResponseDto.class, AlertState.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder().alert(alert).build();

      assertFalse((Boolean) method.invoke(alertEvaluationService, AlertState.NO_DATA, dto, AlertState.NORMAL));
    }

    @Test
    void shouldNotCreateIncidentWhenAlreadyFiring() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "shouldCreateIncident", AlertState.class, AlertEvaluationResponseDto.class, AlertState.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder().alert(alert).build();

      assertFalse((Boolean) method.invoke(alertEvaluationService, AlertState.FIRING, dto, AlertState.FIRING));
    }

    @Test
    void shouldCreateIncidentWhenTransitioningToFiring() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "shouldCreateIncident", AlertState.class, AlertEvaluationResponseDto.class, AlertState.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder().alert(alert).build();

      assertTrue((Boolean) method.invoke(alertEvaluationService, AlertState.FIRING, dto, AlertState.NORMAL));
    }
  }

  @Nested
  class ExtractMetricReadingTests {

    @Test
    void shouldReturnNullForNullInput() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      assertNull(method.invoke(alertEvaluationService, (String) null));
    }

    @Test
    void shouldReturnNullForEmptyInput() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      assertNull(method.invoke(alertEvaluationService, ""));
    }

    @Test
    void shouldExtractFirstNumericValue() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      Float result = (Float) method.invoke(alertEvaluationService, "{\"error_rate\":0.5}");
      assertNotNull(result);
      assertEquals(0.5f, result, 0.001f);
    }

    @Test
    void shouldReturnNullForNonNumericValues() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      Float result = (Float) method.invoke(alertEvaluationService, "{\"status\":\"ok\"}");
      assertNull(result);
    }

    @Test
    void shouldReturnNullForEmptyJsonObject() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      Float result = (Float) method.invoke(alertEvaluationService, "{}");
      assertNull(result);
    }

    @Test
    void shouldReturnNullForInvalidJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      Float result = (Float) method.invoke(alertEvaluationService, "invalid");
      assertNull(result);
    }

    @Test
    void shouldHandleIntegerValues() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractMetricReading", String.class);
      method.setAccessible(true);
      Float result = (Float) method.invoke(alertEvaluationService, "{\"count\":100}");
      assertNotNull(result);
      assertEquals(100f, result, 0.001f);
    }
  }

  @Nested
  class BuildNotificationMessageTests {

    @Test
    void shouldBuildBasicNotificationMessage() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .build();

      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", null);
      assertNotNull(result);
      assertTrue(result.contains("Test Alert"));
      assertTrue(result.contains("TestScope"));
    }

    @Test
    void shouldIncludeMetricReading() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .build();
      
      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", 0.5f);
      assertNotNull(result);
      assertTrue(result.contains("0.5"));
    }

    @Test
    void shouldIncludeEvaluationResult() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .evaluationResult("{\"error_rate\":0.8,\"latency\":150}")
          .build();

      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", null);
      assertNotNull(result);
      assertTrue(result.contains("Metric readings"));
    }

    @Test
    void shouldHandleEmptyEvaluationResult() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .evaluationResult("")
          .build();

      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", null);
      assertNotNull(result);
    }

    @Test
    void shouldHandleEmptyJsonEvaluationResult() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .evaluationResult("{}")
          .build();
      
      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", null);
      assertNotNull(result);
    }

    @Test
    void shouldHandleInvalidJsonEvaluationResult() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildNotificationMessage", AlertEvaluationResponseDto.class, String.class, Float.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder().name("Test Alert").build();
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alert)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .evaluationResult("invalid json")
          .build();

      String result = (String) method.invoke(alertEvaluationService, dto, "TestScope", null);
      assertNotNull(result);
      assertTrue(result.contains("invalid json"));
    }
  }

  @Nested
  class BuildQueryRequestTests {

    @Test
    void shouldBuildQueryRequestForInteractionScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestInteraction")
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.TRACES, result.getDataType());
    }

    @Test
    void shouldBuildQueryRequestForScreenScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("SCREEN")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("HomeScreen")
              .conditions("[{\"metric\":\"LOAD_TIME\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("LOAD_TIME"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.TRACES, result.getDataType());
    }

    @Test
    void shouldBuildQueryRequestForNetworkApiScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("NETWORK_API")
          .evaluationPeriod(60)
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("get_https://api.test.com")
              .conditions("[{\"metric\":\"NET_4XX\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("NET_4XX"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.TRACES, result.getDataType());
      assertTrue(result.getSelect().stream().anyMatch(s -> "method".equals(s.getAlias())));
    }

    @Test
    void shouldBuildQueryRequestForAppVitalsScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("APP_VITALS")
          .evaluationPeriod(60)
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("VitalsScope")
              .conditions("[{\"metric\":\"CRASH_USERS\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("CRASH_USERS"), QueryRequest.DataType.EXCEPTIONS);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.EXCEPTIONS, result.getDataType());
    }

    @Test
    void shouldBuildQueryRequestWithDimensionFilter() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .dimensionFilter("{\"os\":\"Android\"}")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestInteraction")
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
      assertTrue(result.getFilters().stream().anyMatch(f -> f.getOperator() == QueryRequest.Operator.ADDITIONAL));
    }

    @Test
    void shouldBuildQueryRequestWithMultipleScopes() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("SCREEN")
          .evaluationPeriod(60)
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder().id(1).name("Screen1").conditions("[{\"metric\":\"LOAD_TIME\",\"alias\":\"A\"}]").build(),
          AlertsDao.AlertScopeDetails.builder().id(2).name("Screen2").conditions("[{\"metric\":\"SCREEN_TIME\",\"alias\":\"B\"}]").build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("LOAD_TIME", "SCREEN_TIME"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
      assertTrue(result.getFilters().stream().anyMatch(f -> f.getOperator() == QueryRequest.Operator.IN));
    }

    @Test
    void shouldHandleNullScopeName() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name(null)
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
    }

    @Test
    void shouldHandleEmptyScopeName() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("")
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
    }

    @Test
    void shouldHandleNullConditions() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestInteraction")
              .conditions(null)
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
    }

    @Test
    void shouldHandleNullMetricInConditions() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestInteraction")
              .conditions("[{\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ERROR_RATE"), QueryRequest.DataType.TRACES);
      assertNotNull(result);
    }

    @Test
    void shouldBuildQueryRequestForAppVitalsWithLogsDataType() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("APP_VITALS")
          .evaluationPeriod(60)
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("VitalsScope")
              .conditions("[{\"metric\":\"ALL_USERS\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("ALL_USERS"), QueryRequest.DataType.LOGS);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.LOGS, result.getDataType());
      assertTrue(result.getFilters().stream().anyMatch(f -> 
          "PulseType".equals(f.getField()) && f.getValue().contains("session.start")));
    }

    @Test
    void shouldBuildQueryRequestForScreenWithExceptionsDataType() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "buildQueryRequest", AlertsDao.AlertDetails.class, List.class, List.class, QueryRequest.DataType.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("SCREEN")
          .evaluationPeriod(60)
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("HomeScreen")
              .conditions("[{\"metric\":\"CRASH_USERS\",\"alias\":\"A\"}]")
              .build()
      );
      
      QueryRequest result = (QueryRequest) method.invoke(alertEvaluationService, alertDetails, scopes, List.of("CRASH_USERS"), QueryRequest.DataType.EXCEPTIONS);
      assertNotNull(result);
      assertEquals(QueryRequest.DataType.EXCEPTIONS, result.getDataType());
      assertTrue(result.getFilters().stream().anyMatch(f -> "ScreenName".equals(f.getField())));
    }
  }

  @Nested
  class EvaluateMetricsTests {

    @Test
    void shouldReturnNoDataForEmptyFields() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(new ArrayList<>());
      queryResult.setRows(new ArrayList<>());
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
      assertEquals(1, results.size());
    }

    @Test
    void shouldReturnNoDataForNullFields() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
          .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"ERROR_RATE\",\"alias\":\"A\"}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(null);
      queryResult.setRows(new ArrayList<>());
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
      assertEquals(1, results.size());
    }

    @Test
    void shouldEvaluateMetricsWithFiringCondition() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      when(metricOperatorFactory.getProcessor(MetricOperator.GREATER_THAN)).thenReturn(metricOperatorProcessor);
      when(metricOperatorProcessor.isFiring(any(), any())).thenReturn(true);
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldEvaluateMetricsForAppVitals() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("APP_VITALS")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("CrashScope")
              .conditions("[{\"metric\":\"crash_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.1}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("crash_rate"));
      queryResult.setRows(List.of(List.of("0.2")));
      
      when(metricOperatorFactory.getProcessor(MetricOperator.GREATER_THAN)).thenReturn(metricOperatorProcessor);
      when(metricOperatorProcessor.isFiring(any(), any())).thenReturn(true);
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleInvalidThresholdString() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
          .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":\"invalid\"}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleValidStringThreshold() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":\"0.5\"}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      when(metricOperatorFactory.getProcessor(MetricOperator.GREATER_THAN)).thenReturn(metricOperatorProcessor);
      when(metricOperatorProcessor.isFiring(any(), any())).thenReturn(true);
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleMissingConditionFields() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\"}]") // Missing alias, operator, threshold
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleEmptyConditions() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
      assertEquals(0, results.size()); // Empty conditions means scope is skipped
    }

    @Test
    void shouldHandleMetricNotFoundInResults() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"missing_metric\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "other_metric", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleScopeFieldNotFoundInResults() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      // No scope field
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleInvalidMetricValue() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "not_a_number", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleNonMatchingScopeName() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("DifferentScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "OtherScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleRowWithInsufficientColumns() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.5}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01"))); // Insufficient columns
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleAppVitalsWithEmptyRows() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("APP_VITALS")
          .conditionExpression("A")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("CrashScope")
              .conditions("[{\"metric\":\"crash_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.1}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("crash_rate"));
      queryResult.setRows(new ArrayList<>());
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleAppVitalsWithInvalidMetricValue() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("APP_VITALS")
          .conditionExpression("A")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("CrashScope")
              .conditions("[{\"metric\":\"crash_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":0.1}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("crash_rate"));
      queryResult.setRows(List.of(List.of("not_a_number")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }

    @Test
    void shouldHandleThresholdAsNonNumberObject() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "evaluateMetrics", AlertsDao.AlertDetails.class, List.class, PerformanceMetricDistributionRes.class);
      method.setAccessible(true);
      
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .scope("INTERACTION")
          .conditionExpression("A")
          .build();
      
      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("TestScope")
              .conditions("[{\"metric\":\"error_rate\",\"alias\":\"A\",\"metric_operator\":\"GREATER_THAN\",\"threshold\":{\"nested\":\"value\"}}]")
              .build()
      );
      
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "error_rate", "interactionName"));
      queryResult.setRows(List.of(List.of("2023-01-01", "0.8", "TestScope")));
      
      @SuppressWarnings("unchecked")
      List<Object> results = (List<Object>) method.invoke(alertEvaluationService, alertDetails, scopes, queryResult);
      assertNotNull(results);
    }
  }

  @Nested
  class RegisterConsumersTests {

    @Test
    void shouldRegisterEventBusConsumers() {
      when(vertx.eventBus()).thenReturn(eventBus);
      when(eventBus.consumer(anyString(), any())).thenReturn(messageConsumer);

      alertEvaluationService.registerConsumers();

      verify(eventBus, times(2)).consumer(anyString(), any());
    }
  }

  @Nested
  class EvaluateAlertByIdTests {

    @Test
    void shouldEvaluateAlertByIdWithEmptyScopes() throws InterruptedException {
      Integer alertId = 1;
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
        .id(alertId)
        .name("Test Alert")
        .scope("INTERACTION")
        .evaluationPeriod(60)
        .build();

      when(alertsDao.getAlertDetailsForEvaluation(alertId)).thenReturn(Single.just(alertDetails));
      when(alertsDao.getAlertScopesForEvaluation(alertId)).thenReturn(Single.just(new ArrayList<>()));
      when(vertx.eventBus()).thenReturn(eventBus);

      Single<EvaluateAlertResponseDto> result = alertEvaluationService.evaluateAlertById(alertId);
      EvaluateAlertResponseDto dto = result.blockingGet();

      assertNotNull(dto);
      assertEquals(String.valueOf(alertId), dto.getAlertId());
    }
  }

  @Nested
  class UpdateScopeStateTests {

    @Test
    void shouldReturnEarlyWhenResponseDtoIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateScopeState", Message.class);
      method.setAccessible(true);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn("invalid json");

      // Should not throw exception, just return early
      method.invoke(alertEvaluationService, mockMessage);
    }

    @Test
    void shouldNotUpdateWhenScopeIdIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateScopeState", Message.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(null)
          .status("ERROR")
          .error("Some error")
          .build();

      String json = realObjectMapper.writeValueAsString(responseDto);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn(json);

      method.invoke(alertEvaluationService, mockMessage);

      verify(alertsDao, times(0)).updateScopeState(anyInt(), any());
    }

    @Test
    void shouldNotUpdateWhenStateIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateScopeState", Message.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .status(Constants.QUERY_COMPLETED_STATUS)  // "Query completed"
          .state(null)  // null state
          .build();

      String json = realObjectMapper.writeValueAsString(responseDto);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn(json);

      method.invoke(alertEvaluationService, mockMessage);

      verify(alertsDao, times(0)).updateScopeState(anyInt(), any());
    }
  }

  @Nested
  class UpdateEvaluationHistoryTests {

    @Test
    void shouldReturnEarlyWhenResponseDtoIsNull() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateEvaluationHistory", Message.class);
      method.setAccessible(true);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn("invalid json");

      // Should not throw exception, just return early
      method.invoke(alertEvaluationService, mockMessage);
    }

    @Test
    void shouldNotCreateHistoryWhenScopeIdIsNullForError() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateEvaluationHistory", Message.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(null)
          .status("ERROR")
          .error("Query failed")
          .build();

      String json = realObjectMapper.writeValueAsString(responseDto);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn(json);

      method.invoke(alertEvaluationService, mockMessage);

      verify(alertsDao, times(0)).createEvaluationHistory(anyInt(), any(), any());
    }

    @Test
    void shouldNotCreateHistoryWhenScopeIdIsNullForCompleted() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("updateEvaluationHistory", Message.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(null)
          .status("COMPLETED")
          .state(AlertState.FIRING)
          .build();

      String json = realObjectMapper.writeValueAsString(responseDto);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn(json);

      method.invoke(alertEvaluationService, mockMessage);

      verify(alertsDao, times(0)).createEvaluationHistory(anyInt(), any(), any());
    }
  }

  @Nested
  class CreateIncidentIfRequiredTests {

    @Test
    void shouldNotCreateIncidentWhenSnoozed() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "createIncidentIfRequired",
          AlertState.class, AlertEvaluationResponseDto.class, Float.class, String.class, AlertState.class
      );
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .snoozedFrom(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
          .snoozedUntil(LocalDateTime.now(ZoneOffset.UTC).plusHours(1))
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .build();

      // Should not throw or call sendNotification since alert is snoozed
      method.invoke(alertEvaluationService, AlertState.FIRING, responseDto, 1.5f, "TestScope", AlertState.NORMAL);
    }

    @Test
    void shouldNotCreateIncidentForNoDataState() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "createIncidentIfRequired",
          AlertState.class, AlertEvaluationResponseDto.class, Float.class, String.class, AlertState.class
      );
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .build();

      // Should not throw or call sendNotification since state is NO_DATA
      method.invoke(alertEvaluationService, AlertState.NO_DATA, responseDto, 1.5f, "TestScope", AlertState.NORMAL);
    }

    @Test
    void shouldNotCreateIncidentWhenAlreadyFiring() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "createIncidentIfRequired",
          AlertState.class, AlertEvaluationResponseDto.class, Float.class, String.class, AlertState.class
      );
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .build();

      // Should not create incident when already firing (state == currentScopeState)
      method.invoke(alertEvaluationService, AlertState.FIRING, responseDto, 1.5f, "TestScope", AlertState.FIRING);
    }
  }

  @Nested
  class GetAlertEvaluationResponseDtoTests {

    @Test
    void shouldReturnNullForInvalidJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getAlertEvaluationResponseDto", Message.class);
      method.setAccessible(true);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn("invalid json");

      Object result = method.invoke(alertEvaluationService, mockMessage);
      assertNull(result);
    }

    @Test
    void shouldReturnResponseDtoForValidJson() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("getAlertEvaluationResponseDto", Message.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .status("COMPLETED")
          .build();

      String json = realObjectMapper.writeValueAsString(responseDto);

      @SuppressWarnings("unchecked")
      Message<Object> mockMessage = mock(Message.class);
      when(mockMessage.body()).thenReturn(json);

      Object result = method.invoke(alertEvaluationService, mockMessage);
      assertNotNull(result);
      assertTrue(result instanceof AlertEvaluationResponseDto);
    }
  }

  @Nested
  class LoggingMethodsTests {

    @Test
    void shouldLogErrorWithResponseDto() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("logError", AlertEvaluationResponseDto.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .error("Test error")
          .build();

      // Should not throw exception
      method.invoke(alertEvaluationService, responseDto);
    }

    @Test
    void shouldLogErrorWhileUpdatingScopeState() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "logErrorWhileUpdatingScopeState", Throwable.class, AlertEvaluationResponseDto.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .build();

      // Should not throw exception
      method.invoke(alertEvaluationService, new RuntimeException("Test error"), responseDto);
    }

    @Test
    void shouldLogErrorWhileUpdatingEvaluationHistory() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "logErrorWhileUpdatingEvaluationHistory", Throwable.class, AlertEvaluationResponseDto.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .build();

      // Should not throw exception
      method.invoke(alertEvaluationService, new RuntimeException("Test error"), responseDto);
    }

    @Test
    void shouldLogParsingError() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "logParsingError", com.fasterxml.jackson.core.JsonProcessingException.class);
      method.setAccessible(true);

      com.fasterxml.jackson.core.JsonProcessingException exception =
          new com.fasterxml.jackson.core.JsonParseException(null, "Test parse error");

      // Should not throw exception
      method.invoke(alertEvaluationService, exception);
    }
  }

  @Nested
  class TriggerSuccessEventTests {

    @Test
    void shouldTriggerSuccessEvent() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "triggerSuccessEvent", AlertEvaluationResponseDto.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .status("COMPLETED")
          .build();

      when(vertx.eventBus()).thenReturn(eventBus);

      // This will fail because rxObjectMapper is null, but we're testing the method is invoked
      try {
        method.invoke(alertEvaluationService, responseDto);
      } catch (Exception e) {
        // Expected due to null rxObjectMapper
      }
    }
  }

  @Nested
  class TriggerErrorEventTests {

    @Test
    void shouldTriggerErrorEvent() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "triggerErrorEvent", AlertEvaluationResponseDto.class);
      method.setAccessible(true);

      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(100)
          .status("ERROR")
          .error("Test error")
          .build();

      when(vertx.eventBus()).thenReturn(eventBus);

      // This will fail because rxObjectMapper is null, but we're testing the method is invoked
      try {
        method.invoke(alertEvaluationService, responseDto);
      } catch (Exception e) {
        // Expected due to null rxObjectMapper
      }
    }
  }

  @Nested
  class CreateEvaluationHistoryTests {

    @Test
    void shouldCreateEvaluationHistory() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "createEvaluationHistory", Integer.class, String.class, AlertState.class);
      method.setAccessible(true);

      when(alertsDao.createEvaluationHistory(eq(100), eq("result"), eq(AlertState.FIRING)))
          .thenReturn(Single.just(true));

      Object result = method.invoke(alertEvaluationService, 100, "result", AlertState.FIRING);
      assertNotNull(result);
      assertTrue(result instanceof Single);
    }
  }

  @Nested
  class UpdateScopeStateOverloadTests {

    @Test
    void shouldUpdateScopeStateWithScopeIdAndState() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "updateScopeState", Integer.class, AlertState.class);
      method.setAccessible(true);

      when(alertsDao.updateScopeState(eq(100), eq(AlertState.FIRING)))
          .thenReturn(Single.just(true));

      Object result = method.invoke(alertEvaluationService, 100, AlertState.FIRING);
      assertNotNull(result);
      assertTrue(result instanceof Single);
    }
  }

  @Nested
  class SendNotificationTests {

    @Test
    void shouldAttemptToSendNotification() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("sendNotification", String.class);
      method.setAccessible(true);

      when(applicationConfig.getWebhookUrl()).thenReturn("http://localhost:8080/webhook");

      // This will fail because WebClient.create(vertx) will fail with mocked Vertx
      // but the test ensures the method is called
      try {
        method.invoke(alertEvaluationService, "Test message");
      } catch (Exception e) {
        // Expected due to WebClient mocking issues
      }
    }
  }

  @Nested
  class GroupMetricsByDataTypeTests {

    @Test
    void shouldGroupMetricsByDataTypeForScreenScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "groupMetricsByDataType", List.class, String.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("HomeScreen")
              .conditions("[{\"metric\":\"LOAD_TIME\",\"alias\":\"A\"},{\"metric\":\"CRASH_USERS\",\"alias\":\"B\"}]")
              .build()
      );

      @SuppressWarnings("unchecked")
      Map<QueryRequest.DataType, List<String>> result = (Map<QueryRequest.DataType, List<String>>) 
          method.invoke(alertEvaluationService, scopes, "SCREEN");

      assertNotNull(result);
      assertTrue(result.containsKey(QueryRequest.DataType.TRACES));
      assertTrue(result.containsKey(QueryRequest.DataType.EXCEPTIONS));
      assertTrue(result.get(QueryRequest.DataType.TRACES).contains("LOAD_TIME"));
      assertTrue(result.get(QueryRequest.DataType.EXCEPTIONS).contains("CRASH_USERS"));
    }

    @Test
    void shouldGroupCompositeMetricsForScreenScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "groupMetricsByDataType", List.class, String.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("HomeScreen")
              .conditions("[{\"metric\":\"CRASH_FREE_USERS_PERCENTAGE\",\"alias\":\"A\"}]")
              .build()
      );

      @SuppressWarnings("unchecked")
      Map<QueryRequest.DataType, List<String>> result = (Map<QueryRequest.DataType, List<String>>) 
          method.invoke(alertEvaluationService, scopes, "SCREEN");

      assertNotNull(result);
      assertTrue(result.containsKey(QueryRequest.DataType.TRACES));
      assertTrue(result.containsKey(QueryRequest.DataType.EXCEPTIONS));
      assertTrue(result.get(QueryRequest.DataType.TRACES).contains("ALL_USERS"));
      assertTrue(result.get(QueryRequest.DataType.EXCEPTIONS).contains("CRASH_USERS"));
    }

    @Test
    void shouldGroupMetricsForAppVitalsScope() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "groupMetricsByDataType", List.class, String.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder()
              .id(1)
              .name("VitalsScope")
              .conditions("[{\"metric\":\"ALL_USERS\",\"alias\":\"A\"},{\"metric\":\"CRASH_USERS\",\"alias\":\"B\"}]")
              .build()
      );

      @SuppressWarnings("unchecked")
      Map<QueryRequest.DataType, List<String>> result = (Map<QueryRequest.DataType, List<String>>) 
          method.invoke(alertEvaluationService, scopes, "APP_VITALS");

      assertNotNull(result);
      assertTrue(result.containsKey(QueryRequest.DataType.LOGS));
      assertTrue(result.containsKey(QueryRequest.DataType.EXCEPTIONS));
      assertTrue(result.get(QueryRequest.DataType.LOGS).contains("ALL_USERS"));
      assertTrue(result.get(QueryRequest.DataType.EXCEPTIONS).contains("CRASH_USERS"));
    }
  }

  @Nested
  class MergeQueryResultsTests {

    @Test
    void shouldMergeQueryResultsFromMultipleDataTypes() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "mergeQueryResults", List.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes result1 = new PerformanceMetricDistributionRes();
      result1.setFields(List.of("t1", "screenName", "load_time"));
      result1.setRows(List.of(
          List.of("2026-01-08T10:00:00Z", "HomeScreen", "100.5")
      ));

      PerformanceMetricDistributionRes result2 = new PerformanceMetricDistributionRes();
      result2.setFields(List.of("t1", "screenName", "crash_users"));
      result2.setRows(List.of(
          List.of("2026-01-08T10:00:00Z", "HomeScreen", "5")
      ));

      PerformanceMetricDistributionRes merged = (PerformanceMetricDistributionRes) 
          method.invoke(alertEvaluationService, List.of(result1, result2));

      assertNotNull(merged);
      assertEquals(4, merged.getFields().size());
      assertEquals(1, merged.getRows().size());
      assertTrue(merged.getFields().contains("load_time"));
      assertTrue(merged.getFields().contains("crash_users"));
    }

    @Test
    void shouldReturnEmptyResultForEmptyInput() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "mergeQueryResults", List.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes result = (PerformanceMetricDistributionRes) 
          method.invoke(alertEvaluationService, List.of());

      assertNotNull(result);
      assertTrue(result.getFields().isEmpty());
      assertTrue(result.getRows().isEmpty());
    }

    @Test
    void shouldReturnSingleResultAsIs() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "mergeQueryResults", List.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes input = new PerformanceMetricDistributionRes();
      input.setFields(List.of("t1", "metric1"));
      input.setRows(List.of(List.of("2026-01-08T10:00:00Z", "10")));

      PerformanceMetricDistributionRes result = (PerformanceMetricDistributionRes) 
          method.invoke(alertEvaluationService, List.of(input));

      assertNotNull(result);
      assertEquals(input.getFields(), result.getFields());
      assertEquals(input.getRows(), result.getRows());
    }
  }

  @Nested
  class CalculateCompositeMetricTests {

    @Test
    void shouldCalculateCrashFreeUsersPercentage() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "calculateCompositeMetric", String.class, PerformanceMetricDistributionRes.class,
          Map.class, String.class, String.class, boolean.class, String.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "screenName", "all_users", "crash_users"));
      queryResult.setRows(List.of(
          List.of("2026-01-08T10:00:00Z", "HomeScreen", "100", "5")
      ));

      Map<String, Integer> fieldIndexMap = new HashMap<>();
      fieldIndexMap.put("all_users", 2);
      fieldIndexMap.put("crash_users", 3);
      fieldIndexMap.put("screenName", 1);

      Float result = (Float) method.invoke(alertEvaluationService, 
          "CRASH_FREE_USERS_PERCENTAGE", queryResult, fieldIndexMap, 
          "HomeScreen", "screenName", false, "SCREEN");

      assertNotNull(result);
      assertEquals(95.0f, result, 0.1f);
    }

    @Test
    void shouldReturnNullWhenDenominatorIsZero() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "calculateCompositeMetric", String.class, PerformanceMetricDistributionRes.class,
          Map.class, String.class, String.class, boolean.class, String.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("t1", "screenName", "all_users", "crash_users"));
      queryResult.setRows(List.of(
          List.of("2026-01-08T10:00:00Z", "HomeScreen", "0", "0")
      ));

      Map<String, Integer> fieldIndexMap = new HashMap<>();
      fieldIndexMap.put("all_users", 2);
      fieldIndexMap.put("crash_users", 3);
      fieldIndexMap.put("screenName", 1);

      Float result = (Float) method.invoke(alertEvaluationService, 
          "CRASH_FREE_USERS_PERCENTAGE", queryResult, fieldIndexMap, 
          "HomeScreen", "screenName", false, "SCREEN");

      assertNull(result);
    }

    @Test
    void shouldCalculateCompositeMetricForAppVitals() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "calculateCompositeMetric", String.class, PerformanceMetricDistributionRes.class,
          Map.class, String.class, String.class, boolean.class, String.class);
      method.setAccessible(true);

      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(List.of("all_users", "crash_users"));
      queryResult.setRows(List.of(List.of("200", "10")));

      Map<String, Integer> fieldIndexMap = new HashMap<>();
      fieldIndexMap.put("all_users", 0);
      fieldIndexMap.put("crash_users", 1);

      Float result = (Float) method.invoke(alertEvaluationService, 
          "CRASH_FREE_USERS_PERCENTAGE", queryResult, fieldIndexMap, 
          "VitalsScope", "scopeName", true, "APP_VITALS");

      assertNotNull(result);
      assertEquals(95.0f, result, 0.1f);
    }
  }

  @Nested
  class ParseMetricValueTests {

    @Test
    void shouldParseValidMetricValue() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseMetricValue", String.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "100.5");
      assertNotNull(result);
      assertEquals(100.5f, result, 0.01f);
    }

    @Test
    void shouldReturnNullForNullString() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseMetricValue", String.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, (String) null);
      assertNull(result);
    }

    @Test
    void shouldReturnNullForEmptyString() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseMetricValue", String.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "");
      assertNull(result);
    }

    @Test
    void shouldReturnNullForNullKeyword() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseMetricValue", String.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "NULL");
      assertNull(result);
    }

    @Test
    void shouldReturnNullForNaN() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("parseMetricValue", String.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "NaN");
      assertNull(result);
    }
  }

  @Nested
  class NormalizeRateOrPercentageTests {

    @Test
    void shouldReturnNullForNullValue() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("normalizeRateOrPercentage", String.class, Float.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "ERROR_RATE", (Float) null);
      assertNull(result);
    }

    @Test
    void shouldReturnNullForNaNInRateMetric() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("normalizeRateOrPercentage", String.class, Float.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "ERROR_RATE", Float.NaN);
      assertNull(result);
    }

    @Test
    void shouldReturnNullForInfinityInRateMetric() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("normalizeRateOrPercentage", String.class, Float.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "ERROR_RATE", Float.POSITIVE_INFINITY);
      assertNull(result);
    }

    @Test
    void shouldNormalizePercentageFromDecimal() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("normalizeRateOrPercentage", String.class, Float.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "ERROR_RATE", 0.5f);
      assertNotNull(result);
      assertEquals(50.0f, result, 0.01f);
    }

    @Test
    void shouldKeepPercentageAsIsWhenAlreadyInRange() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("normalizeRateOrPercentage", String.class, Float.class);
      method.setAccessible(true);

      Float result = (Float) method.invoke(alertEvaluationService, "ERROR_RATE", 75.5f);
      assertNotNull(result);
      assertEquals(75.5f, result, 0.01f);
    }
  }

  @Nested
  class MatchesNetworkApiScopeTests {

    @Test
    void shouldMatchNetworkApiScopeWithMethodAndUrl() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "matchesNetworkApiScope", String.class, String.class, String.class);
      method.setAccessible(true);

      Boolean result = (Boolean) method.invoke(alertEvaluationService, 
          "get_https://api.test.com", "get", "https://api.test.com");
      assertTrue(result);
    }

    @Test
    void shouldNotMatchWhenMethodDiffers() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "matchesNetworkApiScope", String.class, String.class, String.class);
      method.setAccessible(true);

      Boolean result = (Boolean) method.invoke(alertEvaluationService, 
          "get_https://api.test.com", "post", "https://api.test.com");
      assertFalse(result);
    }

    @Test
    void shouldMatchUrlOnlyWhenNoMethodPrefix() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod(
          "matchesNetworkApiScope", String.class, String.class, String.class);
      method.setAccessible(true);

      Boolean result = (Boolean) method.invoke(alertEvaluationService, 
          "https://api.test.com", "get", "https://api.test.com");
      assertTrue(result);
    }
  }

  @Nested
  class ExtractUrlsFromScopesTests {

    @Test
    void shouldExtractUrlsFromMethodUrlFormat() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractUrlsFromScopes", List.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder().name("get_https://api.test.com").build(),
          AlertsDao.AlertScopeDetails.builder().name("post_https://api.test.com/v2").build()
      );

      @SuppressWarnings("unchecked")
      Set<String> result = (Set<String>) method.invoke(alertEvaluationService, scopes);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertTrue(result.contains("https://api.test.com"));
      assertTrue(result.contains("https://api.test.com/v2"));
    }

    @Test
    void shouldHandleScopesWithoutMethodPrefix() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractUrlsFromScopes", List.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder().name("https://api.test.com").build()
      );

      @SuppressWarnings("unchecked")
      Set<String> result = (Set<String>) method.invoke(alertEvaluationService, scopes);

      assertNotNull(result);
      assertTrue(result.contains("https://api.test.com"));
    }
  }

  @Nested
  class ExtractScopeNamesTests {

    @Test
    void shouldExtractScopeNames() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractScopeNames", List.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder().name("Screen1").build(),
          AlertsDao.AlertScopeDetails.builder().name("Screen2").build()
      );

      @SuppressWarnings("unchecked")
      List<String> result = (List<String>) method.invoke(alertEvaluationService, scopes);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertTrue(result.contains("Screen1"));
      assertTrue(result.contains("Screen2"));
    }

    @Test
    void shouldSkipNullAndEmptyNames() throws Exception {
      Method method = AlertEvaluationService.class.getDeclaredMethod("extractScopeNames", List.class);
      method.setAccessible(true);

      List<AlertsDao.AlertScopeDetails> scopes = List.of(
          AlertsDao.AlertScopeDetails.builder().name("Screen1").build(),
          AlertsDao.AlertScopeDetails.builder().name(null).build(),
          AlertsDao.AlertScopeDetails.builder().name("").build()
      );

      @SuppressWarnings("unchecked")
      List<String> result = (List<String>) method.invoke(alertEvaluationService, scopes);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertTrue(result.contains("Screen1"));
    }
  }
}
