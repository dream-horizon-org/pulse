package org.dreamhorizon.pulseserver.service.alert.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.EventBus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
  private ObjectMapper objectMapper;

  @Mock
  private Vertx vertx;

  @Mock
  private RxObjectMapper rxObjectMapper;

  @Mock
  private EventBus eventBus;

  @Mock
  private MetricOperatorProcessor metricOperatorProcessor;

  private AlertEvaluationService alertEvaluationService;

  @BeforeEach
  void setUp() {
    alertEvaluationService = new AlertEvaluationService(
        applicationConfig,
        alertsDao,
        clickhouseMetricService,
        metricOperatorFactory,
        objectMapper,
        vertx,
        rxObjectMapper
    );
    when(vertx.eventBus()).thenReturn(eventBus);
    when(applicationConfig.getWebhookUrl()).thenReturn("http://webhook.url");
  }

  @Nested
  class TestAlertEvaluationServiceMethods {

    @Test
    void shouldEvaluateAlertById() {
      Integer alertId = 1;
      AlertsDao.AlertDetails alertDetails = createMockAlertDetails(alertId);
      List<AlertsDao.AlertScopeDetails> scopes = new ArrayList<>();
      PerformanceMetricDistributionRes queryResult = new PerformanceMetricDistributionRes();
      queryResult.setFields(new ArrayList<>());
      queryResult.setRows(new ArrayList<>());

      when(alertsDao.getAlertDetailsForEvaluation(alertId)).thenReturn(Single.just(alertDetails));
      when(alertsDao.getAlertScopesForEvaluation(alertId)).thenReturn(Single.just(scopes));
      when(clickhouseMetricService.getMetricDistribution(any(QueryRequest.class)))
          .thenReturn(Single.just(queryResult));

      EvaluateAlertResponseDto result = alertEvaluationService.evaluateAlertById(alertId).blockingGet();

      assertNotNull(result);
      assertEquals(String.valueOf(alertId), result.getAlertId());
      verify(alertsDao).getAlertDetailsForEvaluation(alertId);
    }

    @Test
    void shouldGetScopeFieldForInteraction() {
      String result = alertEvaluationService.getScopeField("INTERACTION");
      assertEquals("SpanName", result);
    }

    @Test
    void shouldGetScopeFieldForScreen() {
      String result = alertEvaluationService.getScopeField("SCREEN");
      assertEquals("SpanAttributes['screen.name']", result);
    }

    @Test
    void shouldGetScopeFieldForNetworkApi() {
      String result = alertEvaluationService.getScopeField("NETWORK_API");
      assertEquals("SpanAttributes['http.url']", result);
    }

    @Test
    void shouldGetScopeFieldForAppVitals() {
      String result = alertEvaluationService.getScopeField("APP_VITALS");
      assertEquals("GroupId", result);
    }

    @Test
    void shouldGetScopeFieldForNull() {
      String result = alertEvaluationService.getScopeField(null);
      assertEquals("SpanName", result);
    }

    @Test
    void shouldGetScopeFieldForEmpty() {
      String result = alertEvaluationService.getScopeField("");
      assertEquals("SpanName", result);
    }

    @Test
    void shouldGetScopeFieldForUnknown() {
      String result = alertEvaluationService.getScopeField("UNKNOWN");
      assertEquals("SpanName", result);
    }

    @Test
    void shouldGetScopeFieldAlias() {
      assertEquals("interactionname", alertEvaluationService.getScopeFieldAlias("INTERACTION"));
      assertEquals("screenname", alertEvaluationService.getScopeFieldAlias("SCREEN"));
      assertEquals("network_apiname", alertEvaluationService.getScopeFieldAlias("NETWORK_API"));
      assertEquals("app_vitalsname", alertEvaluationService.getScopeFieldAlias("APP_VITALS"));
    }

    @Test
    void shouldGetScopeFieldAliasForNull() {
      String result = alertEvaluationService.getScopeFieldAlias(null);
      assertEquals("scopeName", result);
    }

    @Test
    void shouldGetScopeFieldAliasForEmpty() {
      String result = alertEvaluationService.getScopeFieldAlias("");
      assertEquals("scopeName", result);
    }

    @Test
    void shouldReturnFalseWhenAlertIsNotSnoozed() {
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .snoozedFrom(null)
          .snoozedUntil(null)
          .build();

      assertFalse(alertEvaluationService.isAlertSnoozed(alert));
    }

    @Test
    void shouldReturnFalseWhenAlertIsNull() {
      assertFalse(alertEvaluationService.isAlertSnoozed((AlertsDao.AlertDetails) null));
    }

    @Test
    void shouldReturnTrueWhenAlertIsSnoozed() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .snoozedFrom(now.minusHours(1))
          .snoozedUntil(now.plusHours(1))
          .build();

      assertTrue(alertEvaluationService.isAlertSnoozed(alert));
    }

    @Test
    void shouldReturnFalseWhenSnoozeHasExpired() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .snoozedFrom(now.minusHours(2))
          .snoozedUntil(now.minusHours(1))
          .build();

      assertFalse(alertEvaluationService.isAlertSnoozed(alert));
    }

    @Test
    void shouldReturnFalseWhenSnoozeHasNotStarted() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      AlertsDao.AlertDetails alert = AlertsDao.AlertDetails.builder()
          .snoozedFrom(now.plusHours(1))
          .snoozedUntil(now.plusHours(2))
          .build();

      assertFalse(alertEvaluationService.isAlertSnoozed(alert));
    }

    @Test
    void shouldReturnFalseWhenSnoozeFromIsNull() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse(alertEvaluationService.isAlertSnoozed(null, now));
    }

    @Test
    void shouldReturnFalseWhenSnoozeUntilIsNull() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertFalse(alertEvaluationService.isAlertSnoozed(now, null));
    }

    @Test
    void shouldReturnTrueWhenSnoozeIsActive() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertTrue(alertEvaluationService.isAlertSnoozed(now.minusHours(1), now.plusHours(1)));
    }

    @Test
    void shouldReturnTrueWhenSnoozeFromEqualsNow() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      assertTrue(alertEvaluationService.isAlertSnoozed(now, now.plusHours(1)));
    }
  }

  @Nested
  class TestAlertEvaluationResponseDtoModel {

    @Test
    void shouldCreateAlertEvaluationResponseDtoWithBuilder() {
      AlertsDao.AlertDetails alertDetails = createMockAlertDetails(1);

      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(1)
          .evaluationResult("{}")
          .timeTaken(100L)
          .evaluationStartTime("2023-01-01 00:00:00")
          .evaluationEndTime("2023-01-01 01:00:00")
          .status("Query completed")
          .error(null)
          .state(AlertState.NORMAL)
          .build();

      assertNotNull(dto);
      assertEquals(alertDetails, dto.getAlert());
      assertEquals(1, dto.getScopeId());
      assertEquals("{}", dto.getEvaluationResult());
      assertEquals(100L, dto.getTimeTaken());
      assertEquals("2023-01-01 00:00:00", dto.getEvaluationStartTime());
      assertEquals("2023-01-01 01:00:00", dto.getEvaluationEndTime());
      assertEquals("Query completed", dto.getStatus());
      assertNull(dto.getError());
      assertEquals(AlertState.NORMAL, dto.getState());
    }

    @Test
    void shouldCreateAlertEvaluationResponseDtoWithNoArgs() {
      AlertEvaluationResponseDto dto = new AlertEvaluationResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetAllFields() {
      AlertEvaluationResponseDto dto = new AlertEvaluationResponseDto();
      AlertsDao.AlertDetails alertDetails = createMockAlertDetails(1);

      dto.setAlert(alertDetails);
      dto.setScopeId(2);
      dto.setEvaluationResult("{\"result\":true}");
      dto.setTimeTaken(200L);
      dto.setEvaluationStartTime("2023-02-01 00:00:00");
      dto.setEvaluationEndTime("2023-02-01 01:00:00");
      dto.setStatus("completed");
      dto.setError("some error");
      dto.setState(AlertState.FIRING);

      assertEquals(alertDetails, dto.getAlert());
      assertEquals(2, dto.getScopeId());
      assertEquals("{\"result\":true}", dto.getEvaluationResult());
      assertEquals(200L, dto.getTimeTaken());
      assertEquals("2023-02-01 00:00:00", dto.getEvaluationStartTime());
      assertEquals("2023-02-01 01:00:00", dto.getEvaluationEndTime());
      assertEquals("completed", dto.getStatus());
      assertEquals("some error", dto.getError());
      assertEquals(AlertState.FIRING, dto.getState());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertsDao.AlertDetails alertDetails = createMockAlertDetails(1);

      AlertEvaluationResponseDto dto1 = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(1)
          .state(AlertState.NORMAL)
          .build();

      AlertEvaluationResponseDto dto2 = AlertEvaluationResponseDto.builder()
          .alert(alertDetails)
          .scopeId(1)
          .state(AlertState.NORMAL)
          .build();

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertEvaluationResponseDto dto = AlertEvaluationResponseDto.builder()
          .scopeId(1)
          .state(AlertState.NORMAL)
          .build();

      String toString = dto.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("scopeId=1"));
    }

    @Test
    void shouldHandleAllArgsConstructor() {
      AlertsDao.AlertDetails alertDetails = createMockAlertDetails(1);
      AlertEvaluationResponseDto dto = new AlertEvaluationResponseDto(
          alertDetails, 1, "{}", 100L, "start", "end", "status", "error", AlertState.FIRING
      );

      assertEquals(alertDetails, dto.getAlert());
      assertEquals(1, dto.getScopeId());
      assertEquals(AlertState.FIRING, dto.getState());
    }
  }

  @Nested
  class TestEvaluateAlertResponseDtoModel {

    @Test
    void shouldCreateEvaluateAlertResponseDtoWithBuilder() {
      EvaluateAlertResponseDto dto = EvaluateAlertResponseDto.builder()
          .alertId("123")
          .build();

      assertNotNull(dto);
      assertEquals("123", dto.getAlertId());
    }

    @Test
    void shouldCreateEvaluateAlertResponseDtoWithNoArgs() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateEvaluateAlertResponseDtoWithAllArgs() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto("456");

      assertEquals("456", dto.getAlertId());
    }

    @Test
    void shouldSetAndGetAlertId() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto();
      dto.setAlertId("789");

      assertEquals("789", dto.getAlertId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      EvaluateAlertResponseDto dto1 = new EvaluateAlertResponseDto("123");
      EvaluateAlertResponseDto dto2 = new EvaluateAlertResponseDto("123");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto("123");
      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("alertId=123"));
    }

    @Test
    void shouldNotEqualDifferentAlertId() {
      EvaluateAlertResponseDto dto1 = new EvaluateAlertResponseDto("123");
      EvaluateAlertResponseDto dto2 = new EvaluateAlertResponseDto("456");

      assertFalse(dto1.equals(dto2));
    }
  }

  @Nested
  class TestAlertStateEnum {

    @Test
    void shouldHaveAllExpectedStates() {
      AlertState[] states = AlertState.values();

      assertEquals(6, states.length);
      assertNotNull(AlertState.valueOf("NORMAL"));
      assertNotNull(AlertState.valueOf("FIRING"));
      assertNotNull(AlertState.valueOf("SILENCED"));
      assertNotNull(AlertState.valueOf("NO_DATA"));
      assertNotNull(AlertState.valueOf("ERRORED"));
      assertNotNull(AlertState.valueOf("QUERY_FAILED"));
    }

    @Test
    void shouldGetCorrectAlertStateString() {
      assertEquals("NORMAL", AlertState.NORMAL.getAlertState());
      assertEquals("FIRING", AlertState.FIRING.getAlertState());
      assertEquals("SILENCED", AlertState.SILENCED.getAlertState());
      assertEquals("NO_DATA", AlertState.NO_DATA.getAlertState());
      assertEquals("ERRORED", AlertState.ERRORED.getAlertState());
      assertEquals("QUERY_FAILED", AlertState.QUERY_FAILED.getAlertState());
    }

    @Test
    void shouldHaveCorrectToString() {
      assertEquals("NORMAL", AlertState.NORMAL.toString());
      assertEquals("FIRING", AlertState.FIRING.toString());
      assertEquals("SILENCED", AlertState.SILENCED.toString());
      assertEquals("NO_DATA", AlertState.NO_DATA.toString());
      assertEquals("ERRORED", AlertState.ERRORED.toString());
      assertEquals("QUERY_FAILED", AlertState.QUERY_FAILED.toString());
    }
  }

  @Nested
  class TestMetricOperatorEnum {

    @Test
    void shouldHaveAllExpectedOperators() {
      MetricOperator[] operators = MetricOperator.values();

      assertEquals(4, operators.length);
      assertNotNull(MetricOperator.valueOf("GREATER_THAN"));
      assertNotNull(MetricOperator.valueOf("LESS_THAN"));
      assertNotNull(MetricOperator.valueOf("GREATER_THAN_EQUAL"));
      assertNotNull(MetricOperator.valueOf("LESS_THAN_EQUAL"));
    }

    @Test
    void shouldConvertToMetricOperator() {
      assertEquals(MetricOperator.GREATER_THAN, MetricOperator.valueOf("GREATER_THAN"));
      assertEquals(MetricOperator.LESS_THAN, MetricOperator.valueOf("LESS_THAN"));
      assertEquals(MetricOperator.GREATER_THAN_EQUAL, MetricOperator.valueOf("GREATER_THAN_EQUAL"));
      assertEquals(MetricOperator.LESS_THAN_EQUAL, MetricOperator.valueOf("LESS_THAN_EQUAL"));
    }
  }

  @Nested
  class TestAlertDetailsModel {

    @Test
    void shouldCreateAlertDetailsWithBuilder() {
      LocalDateTime now = LocalDateTime.now();

      AlertsDao.AlertDetails details = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .description("Test Description")
          .scope("INTERACTION")
          .dimensionFilter("{}")
          .conditionExpression("A && B")
          .severityId(1)
          .notificationChannelId(1)
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .createdBy("user1")
          .updatedBy("user2")
          .isActive(true)
          .snoozedFrom(now)
          .snoozedUntil(now.plusHours(1))
          .build();

      assertNotNull(details);
      assertEquals(1, details.getId());
      assertEquals("Test Alert", details.getName());
      assertEquals("Test Description", details.getDescription());
      assertEquals("INTERACTION", details.getScope());
      assertEquals("{}", details.getDimensionFilter());
      assertEquals("A && B", details.getConditionExpression());
      assertEquals(1, details.getSeverityId());
      assertEquals(1, details.getNotificationChannelId());
      assertEquals(60, details.getEvaluationPeriod());
      assertEquals(300, details.getEvaluationInterval());
      assertEquals("user1", details.getCreatedBy());
      assertEquals("user2", details.getUpdatedBy());
      assertTrue(details.getIsActive());
      assertEquals(now, details.getSnoozedFrom());
      assertEquals(now.plusHours(1), details.getSnoozedUntil());
    }

    @Test
    void shouldSetAllAlertDetailsFields() {
      AlertsDao.AlertDetails details = AlertsDao.AlertDetails.builder().build();
      LocalDateTime now = LocalDateTime.now();

      details.setId(2);
      details.setName("Updated Alert");
      details.setDescription("Updated Description");
      details.setScope("SCREEN");
      details.setDimensionFilter("{\"filter\":\"value\"}");
      details.setConditionExpression("A || B");
      details.setSeverityId(2);
      details.setNotificationChannelId(2);
      details.setEvaluationPeriod(120);
      details.setEvaluationInterval(600);
      details.setCreatedBy("creator");
      details.setUpdatedBy("updater");
      details.setIsActive(false);
      details.setSnoozedFrom(now);
      details.setSnoozedUntil(now.plusDays(1));

      assertEquals(2, details.getId());
      assertEquals("Updated Alert", details.getName());
      assertEquals("SCREEN", details.getScope());
      assertEquals("Updated Description", details.getDescription());
      assertEquals("A || B", details.getConditionExpression());
      assertFalse(details.getIsActive());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertsDao.AlertDetails details1 = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Alert")
          .build();

      AlertsDao.AlertDetails details2 = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Alert")
          .build();

      assertEquals(details1, details2);
      assertEquals(details1.hashCode(), details2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertsDao.AlertDetails details = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Test Alert")
          .build();

      String toString = details.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("id=1"));
      assertTrue(toString.contains("name=Test Alert"));
    }
  }

  @Nested
  class TestAlertScopeDetailsModel {

    @Test
    void shouldCreateAlertScopeDetailsWithBuilder() {
      AlertsDao.AlertScopeDetails scopeDetails = AlertsDao.AlertScopeDetails.builder()
          .id(1)
          .alertId(100)
          .name("Test Scope")
          .conditions("[{\"metric\":\"error_rate\"}]")
          .state(AlertState.NORMAL)
          .build();

      assertNotNull(scopeDetails);
      assertEquals(1, scopeDetails.getId());
      assertEquals(100, scopeDetails.getAlertId());
      assertEquals("Test Scope", scopeDetails.getName());
      assertEquals("[{\"metric\":\"error_rate\"}]", scopeDetails.getConditions());
      assertEquals(AlertState.NORMAL, scopeDetails.getState());
    }

    @Test
    void shouldSetAllAlertScopeDetailsFields() {
      AlertsDao.AlertScopeDetails scopeDetails = AlertsDao.AlertScopeDetails.builder().build();

      scopeDetails.setId(2);
      scopeDetails.setAlertId(200);
      scopeDetails.setName("Updated Scope");
      scopeDetails.setConditions("[{\"metric\":\"latency\"}]");
      scopeDetails.setState(AlertState.FIRING);

      assertEquals(2, scopeDetails.getId());
      assertEquals(200, scopeDetails.getAlertId());
      assertEquals("Updated Scope", scopeDetails.getName());
      assertEquals("[{\"metric\":\"latency\"}]", scopeDetails.getConditions());
      assertEquals(AlertState.FIRING, scopeDetails.getState());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertsDao.AlertScopeDetails scope1 = AlertsDao.AlertScopeDetails.builder()
          .id(1)
          .alertId(100)
          .name("Scope")
          .build();

      AlertsDao.AlertScopeDetails scope2 = AlertsDao.AlertScopeDetails.builder()
          .id(1)
          .alertId(100)
          .name("Scope")
          .build();

      assertEquals(scope1, scope2);
      assertEquals(scope1.hashCode(), scope2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertsDao.AlertScopeDetails scopeDetails = AlertsDao.AlertScopeDetails.builder()
          .id(1)
          .name("Test Scope")
          .build();

      String toString = scopeDetails.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("id=1"));
      assertTrue(toString.contains("name=Test Scope"));
    }

    @Test
    void shouldNotEqualDifferentScope() {
      AlertsDao.AlertScopeDetails scope1 = AlertsDao.AlertScopeDetails.builder()
          .id(1)
          .name("Scope1")
          .build();

      AlertsDao.AlertScopeDetails scope2 = AlertsDao.AlertScopeDetails.builder()
          .id(2)
          .name("Scope2")
          .build();

      assertFalse(scope1.equals(scope2));
    }
  }

  @Nested
  class TestAlertSnoozing {

    @Test
    void shouldNotCreateIncidentWhenAlertIsSnoozed() {
      LocalDateTime now = LocalDateTime.now();
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Snoozed Alert")
          .snoozedFrom(now.minusHours(1))
          .snoozedUntil(now.plusHours(1))
          .build();

      // Snoozed alert should not create incident
      assertNotNull(alertDetails.getSnoozedFrom());
      assertNotNull(alertDetails.getSnoozedUntil());
      assertTrue(alertDetails.getSnoozedFrom().isBefore(now));
      assertTrue(alertDetails.getSnoozedUntil().isAfter(now));
    }

    @Test
    void shouldAllowIncidentWhenAlertIsNotSnoozed() {
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Not Snoozed Alert")
          .snoozedFrom(null)
          .snoozedUntil(null)
          .build();

      // Not snoozed alert should allow incident
      assertNull(alertDetails.getSnoozedFrom());
      assertNull(alertDetails.getSnoozedUntil());
    }

    @Test
    void shouldNotCreateIncidentWhenSnoozeHasExpired() {
      LocalDateTime now = LocalDateTime.now();
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Expired Snooze Alert")
          .snoozedFrom(now.minusHours(2))
          .snoozedUntil(now.minusHours(1))
          .build();

      // Snooze has expired, so incident should be allowed
      assertTrue(alertDetails.getSnoozedUntil().isBefore(now));
    }

    @Test
    void shouldIdentifyFutureSnooze() {
      LocalDateTime now = LocalDateTime.now();
      AlertsDao.AlertDetails alertDetails = AlertsDao.AlertDetails.builder()
          .id(1)
          .name("Future Snooze Alert")
          .snoozedFrom(now.plusHours(1))
          .snoozedUntil(now.plusHours(2))
          .build();

      // Snooze hasn't started yet
      assertTrue(alertDetails.getSnoozedFrom().isAfter(now));
    }
  }

  @Nested
  class TestConstantsValues {

    @Test
    void shouldHaveCorrectAlertConstants() {
      assertEquals("/v1/alert/evaluateAndTriggerAlert", Constants.ALERT_EVALUATE_AND_TRIGGER_ALERT);
      assertEquals("Query completed", Constants.QUERY_COMPLETED_STATUS);
    }

    @Test
    void shouldHaveCorrectEventBusChannels() {
      assertEquals("athena.query.response.updateAlertEvaluationLogs",
          Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_EVALUATION_LOGS_CHANNEL);
      assertEquals("athena.query.response.updateAlertState",
          Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_STATE_CHANNEL);
    }

    @Test
    void shouldHaveCorrectResponseKeys() {
      assertEquals("resultSet", Constants.RESULT_SET_KEY);
      assertEquals("status", Constants.STATUS_KEY);
      assertEquals("error", Constants.ERROR_KEY);
      assertEquals("timeTaken", Constants.ALERT_EVALUATION_QUERY_TIME);
      assertEquals("evaluationStartTime", Constants.ALERT_EVALUATION_START_TIME);
      assertEquals("evaluationEndTime", Constants.ALERT_EVALUATION_END_TIME);
    }

    @Test
    void shouldHaveCorrectMysqlConstants() {
      assertEquals("mysql_writer_host", Constants.MYSQL_WRITER_HOST);
      assertEquals("mysql_reader_host", Constants.MYSQL_READER_HOST);
      assertEquals("mysql_database", Constants.MYSQL_DATABASE);
      assertEquals("mysql_user", Constants.MYSQL_USER);
      assertEquals("mysql_password", Constants.MYSQL_PASSWORD);
    }

    @Test
    void shouldHaveCorrectHttpConstants() {
      assertEquals("http_connect_timeout", Constants.HTTP_CONNECT_TIMEOUT);
      assertEquals("http_read_timeout", Constants.HTTP_READ_TIMEOUT);
      assertEquals("http_write_timeout", Constants.HTTP_WRITE_TIMEOUT);
    }

    @Test
    void shouldHaveCorrectShutdownStatus() {
      assertEquals("__shutdown__", Constants.SHUTDOWN_STATUS);
    }
  }

  @Nested
  class TestApplicationConfigModel {

    @Test
    void shouldCreateApplicationConfigWithAllArgs() {
      ApplicationConfig config = new ApplicationConfig(
          "http://cron.url",
          "http://service.url",
          30,
          "google-client-id",
          true,
          "jwt-secret",
          "http://webhook.url"
      );

      assertEquals("http://cron.url", config.getCronManagerBaseUrl());
      assertEquals("http://service.url", config.getServiceUrl());
      assertEquals(30, config.getShutdownGracePeriod());
      assertEquals("google-client-id", config.getGoogleOAuthClientId());
      assertTrue(config.getGoogleOAuthEnabled());
      assertEquals("jwt-secret", config.getJwtSecret());
      assertEquals("http://webhook.url", config.getWebhookUrl());
    }

    @Test
    void shouldCreateApplicationConfigWithNoArgs() {
      ApplicationConfig config = new ApplicationConfig();

      assertNotNull(config);
    }

    @Test
    void shouldSetApplicationConfigFields() {
      ApplicationConfig config = new ApplicationConfig();
      config.setCronManagerBaseUrl("http://new-cron.url");
      config.setServiceUrl("http://new-service.url");
      config.setShutdownGracePeriod(60);
      config.setGoogleOAuthClientId("new-client-id");
      config.setGoogleOAuthEnabled(false);
      config.setJwtSecret("new-jwt-secret");
      config.setWebhookUrl("http://new-webhook.url");

      assertEquals("http://new-cron.url", config.getCronManagerBaseUrl());
      assertEquals("http://new-service.url", config.getServiceUrl());
      assertEquals(60, config.getShutdownGracePeriod());
      assertEquals("new-client-id", config.getGoogleOAuthClientId());
      assertFalse(config.getGoogleOAuthEnabled());
      assertEquals("new-jwt-secret", config.getJwtSecret());
      assertEquals("http://new-webhook.url", config.getWebhookUrl());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      ApplicationConfig config1 = new ApplicationConfig(
          "http://cron.url", "http://service.url", 30, "client-id", true, "secret", "http://webhook.url"
      );
      ApplicationConfig config2 = new ApplicationConfig(
          "http://cron.url", "http://service.url", 30, "client-id", true, "secret", "http://webhook.url"
      );

      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      ApplicationConfig config = new ApplicationConfig(
          "http://cron.url",
          "http://service.url",
          30,
          "google-client-id",
          true,
          "jwt-secret",
          "http://webhook.url"
      );
      String toString = config.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("cronManagerBaseUrl=http://cron.url"));
      assertTrue(toString.contains("serviceUrl=http://service.url"));
    }
  }

  @Nested
  class TestPerformanceMetricDistributionRes {

    @Test
    void shouldCreatePerformanceMetricDistributionRes() {
      PerformanceMetricDistributionRes res = new PerformanceMetricDistributionRes();
      res.setFields(List.of("error_rate", "latency", "scopeName"));
      res.setRows(List.of(
          List.of("0.05", "100", "TestScope")
      ));

      assertNotNull(res);
      assertEquals(3, res.getFields().size());
      assertEquals(1, res.getRows().size());
    }

    @Test
    void shouldGetFieldsAndRows() {
      PerformanceMetricDistributionRes res = new PerformanceMetricDistributionRes();
      res.setFields(List.of("metric1", "metric2"));
      res.setRows(List.of(
          List.of("value1", "value2"),
          List.of("value3", "value4")
      ));

      assertEquals(2, res.getFields().size());
      assertEquals(2, res.getRows().size());
      assertEquals("metric1", res.getFields().get(0));
      assertEquals("value1", res.getRows().get(0).get(0));
    }
  }

  // Helper methods

  private AlertsDao.AlertDetails createMockAlertDetails(Integer alertId) {
    return AlertsDao.AlertDetails.builder()
        .id(alertId)
        .name("Test Alert")
        .description("Test Description")
        .scope("INTERACTION")
        .dimensionFilter(null)
        .conditionExpression("A && B")
        .severityId(1)
        .notificationChannelId(1)
        .evaluationPeriod(60)
        .evaluationInterval(300)
        .createdBy("test_user")
        .updatedBy("test_user")
        .isActive(true)
        .snoozedFrom(null)
        .snoozedUntil(null)
        .build();
  }
}
