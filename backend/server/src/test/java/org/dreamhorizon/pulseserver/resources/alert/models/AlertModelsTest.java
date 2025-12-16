package org.dreamhorizon.pulseserver.resources.alert.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertModelsTest {

  // AlertState Enum Tests
  @Nested
  class TestAlertStateEnum {

    @Test
    void shouldHaveAllExpectedValues() {
      AlertState[] states = AlertState.values();
      assertEquals(6, states.length);
    }

    @Test
    void shouldHaveNormalState() {
      AlertState state = AlertState.NORMAL;
      assertEquals("NORMAL", state.getAlertState());
      assertEquals("NORMAL", state.toString());
    }

    @Test
    void shouldHaveFiringState() {
      AlertState state = AlertState.FIRING;
      assertEquals("FIRING", state.getAlertState());
      assertEquals("FIRING", state.toString());
    }

    @Test
    void shouldHaveSilencedState() {
      AlertState state = AlertState.SILENCED;
      assertEquals("SILENCED", state.getAlertState());
      assertEquals("SILENCED", state.toString());
    }

    @Test
    void shouldHaveNoDataState() {
      AlertState state = AlertState.NO_DATA;
      assertEquals("NO_DATA", state.getAlertState());
      assertEquals("NO_DATA", state.toString());
    }

    @Test
    void shouldHaveErroredState() {
      AlertState state = AlertState.ERRORED;
      assertEquals("ERRORED", state.getAlertState());
      assertEquals("ERRORED", state.toString());
    }

    @Test
    void shouldHaveQueryFailedState() {
      AlertState state = AlertState.QUERY_FAILED;
      assertEquals("QUERY_FAILED", state.getAlertState());
      assertEquals("QUERY_FAILED", state.toString());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(AlertState.NORMAL, AlertState.valueOf("NORMAL"));
      assertEquals(AlertState.FIRING, AlertState.valueOf("FIRING"));
      assertEquals(AlertState.SILENCED, AlertState.valueOf("SILENCED"));
      assertEquals(AlertState.NO_DATA, AlertState.valueOf("NO_DATA"));
      assertEquals(AlertState.ERRORED, AlertState.valueOf("ERRORED"));
      assertEquals(AlertState.QUERY_FAILED, AlertState.valueOf("QUERY_FAILED"));
    }
  }

  // AlertConditionDto Tests
  @Nested
  class TestAlertConditionDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertConditionDto dto = new AlertConditionDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("key", 0.5f);
      AlertConditionDto dto = new AlertConditionDto("A", Metric.ERROR_RATE, MetricOperator.GREATER_THAN, threshold);

      assertEquals("A", dto.getAlias());
      assertEquals(Metric.ERROR_RATE, dto.getMetric());
      assertEquals(MetricOperator.GREATER_THAN, dto.getMetricOperator());
      assertEquals(threshold, dto.getThreshold());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertConditionDto dto = new AlertConditionDto();
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.1f);

      dto.setAlias("B");
      dto.setMetric(Metric.CRASH_RATE);
      dto.setMetricOperator(MetricOperator.LESS_THAN);
      dto.setThreshold(threshold);

      assertEquals("B", dto.getAlias());
      assertEquals(Metric.CRASH_RATE, dto.getMetric());
      assertEquals(MetricOperator.LESS_THAN, dto.getMetricOperator());
      assertEquals(threshold, dto.getThreshold());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Map<String, Float> threshold = new HashMap<>();
      AlertConditionDto dto1 = new AlertConditionDto("A", Metric.ERROR_RATE, MetricOperator.GREATER_THAN, threshold);
      AlertConditionDto dto2 = new AlertConditionDto("A", Metric.ERROR_RATE, MetricOperator.GREATER_THAN, threshold);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertConditionDto dto = new AlertConditionDto();
      dto.setAlias("A");
      assertNotNull(dto.toString());
      assertTrue(dto.toString().contains("A"));
    }
  }

  // AlertDetailsResponseDto Tests
  @Nested
  class TestAlertDetailsResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertDetailsResponseDto dto = new AlertDetailsResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithBuilder() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      AlertDetailsResponseDto dto = AlertDetailsResponseDto.builder()
          .alertId(1)
          .name("Test Alert")
          .description("Description")
          .scope("Interaction")
          .dimensionFilter("{}")
          .alerts(new ArrayList<>())
          .conditionExpression("A && B")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severityId(1)
          .notificationChannelId(1)
          .notificationWebhookUrl("http://webhook.url")
          .createdBy("user")
          .updatedBy("user")
          .createdAt(now)
          .updatedAt(now)
          .isActive(true)
          .lastSnoozedAt(LocalDateTime.now())
          .snoozedFrom(System.currentTimeMillis())
          .snoozedUntil(System.currentTimeMillis() + 3600000)
          .isSnoozed(false)
          .build();

      assertEquals(1, dto.getAlertId());
      assertEquals("Test Alert", dto.getName());
      assertEquals("Description", dto.getDescription());
      assertEquals("Interaction", dto.getScope());
      assertEquals("{}", dto.getDimensionFilter());
      assertEquals("A && B", dto.getConditionExpression());
      assertEquals(60, dto.getEvaluationPeriod());
      assertEquals(300, dto.getEvaluationInterval());
      assertEquals(1, dto.getSeverityId());
      assertEquals(1, dto.getNotificationChannelId());
      assertEquals("http://webhook.url", dto.getNotificationWebhookUrl());
      assertEquals("user", dto.getCreatedBy());
      assertEquals("user", dto.getUpdatedBy());
      assertTrue(dto.getIsActive());
    }

    @Test
    void shouldSetAndGetAllFields() {
      AlertDetailsResponseDto dto = new AlertDetailsResponseDto();
      Timestamp now = new Timestamp(System.currentTimeMillis());
      List<AlertConditionDto> alerts = new ArrayList<>();

      dto.setAlertId(2);
      dto.setName("Updated Alert");
      dto.setDescription("Updated Description");
      dto.setScope("network");
      dto.setDimensionFilter("{}");
      dto.setAlerts(alerts);
      dto.setConditionExpression("A || B");
      dto.setEvaluationPeriod(120);
      dto.setEvaluationInterval(600);
      dto.setSeverityId(2);
      dto.setNotificationChannelId(2);
      dto.setNotificationWebhookUrl("http://new.url");
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setCreatedAt(now);
      dto.setUpdatedAt(now);
      dto.setIsActive(false);
      dto.setLastSnoozedAt(LocalDateTime.now());
      dto.setSnoozedFrom(1000L);
      dto.setSnoozedUntil(2000L);
      dto.setIsSnoozed(true);

      assertEquals(2, dto.getAlertId());
      assertEquals("Updated Alert", dto.getName());
      assertEquals("network", dto.getScope());
      assertTrue(dto.getIsSnoozed());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertDetailsResponseDto dto1 = AlertDetailsResponseDto.builder().alertId(1).name("Test").build();
      AlertDetailsResponseDto dto2 = AlertDetailsResponseDto.builder().alertId(1).name("Test").build();

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertDetailsPaginatedResponseDto Tests
  @Nested
  class TestAlertDetailsPaginatedResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertDetailsPaginatedResponseDto dto = new AlertDetailsPaginatedResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();
      AlertDetailsPaginatedResponseDto dto = new AlertDetailsPaginatedResponseDto(10, alerts, 0, 5);

      assertEquals(10, dto.getTotalAlerts());
      assertEquals(alerts, dto.getAlerts());
      assertEquals(0, dto.getPage());
      assertEquals(5, dto.getLimit());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();
      AlertDetailsPaginatedResponseDto dto = AlertDetailsPaginatedResponseDto.builder()
          .totalAlerts(20)
          .alerts(alerts)
          .page(1)
          .limit(10)
          .build();

      assertEquals(20, dto.getTotalAlerts());
      assertEquals(1, dto.getPage());
      assertEquals(10, dto.getLimit());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertDetailsPaginatedResponseDto dto = new AlertDetailsPaginatedResponseDto();
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();

      dto.setTotalAlerts(15);
      dto.setAlerts(alerts);
      dto.setPage(2);
      dto.setLimit(20);

      assertEquals(15, dto.getTotalAlerts());
      assertEquals(alerts, dto.getAlerts());
      assertEquals(2, dto.getPage());
      assertEquals(20, dto.getLimit());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertDetailsPaginatedResponseDto dto1 = AlertDetailsPaginatedResponseDto.builder().totalAlerts(10).page(0).limit(5).build();
      AlertDetailsPaginatedResponseDto dto2 = AlertDetailsPaginatedResponseDto.builder().totalAlerts(10).page(0).limit(5).build();

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AllAlertDetailsResponseDto Tests
  @Nested
  class TestAllAlertDetailsResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AllAlertDetailsResponseDto dto = new AllAlertDetailsResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();
      AllAlertDetailsResponseDto dto = new AllAlertDetailsResponseDto(alerts);

      assertEquals(alerts, dto.getAlerts());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();
      AllAlertDetailsResponseDto dto = AllAlertDetailsResponseDto.builder()
          .alerts(alerts)
          .build();

      assertEquals(alerts, dto.getAlerts());
    }

    @Test
    void shouldSetAndGetAlerts() {
      AllAlertDetailsResponseDto dto = new AllAlertDetailsResponseDto();
      List<AlertDetailsResponseDto> alerts = new ArrayList<>();

      dto.setAlerts(alerts);

      assertEquals(alerts, dto.getAlerts());
    }
  }

  // AlertSeverityResponseDto Tests
  @Nested
  class TestAlertSeverityResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertSeverityResponseDto dto = new AlertSeverityResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertSeverityResponseDto dto = new AlertSeverityResponseDto(1, 1, "Critical");

      assertEquals(1, dto.getSeverity_id());
      assertEquals(1, dto.getName());
      assertEquals("Critical", dto.getDescription());
    }

    @Test
    void shouldCreateWithBuilder() {
      AlertSeverityResponseDto dto = AlertSeverityResponseDto.builder()
          .severity_id(2)
          .name(2)
          .description("High")
          .build();

      assertEquals(2, dto.getSeverity_id());
      assertEquals(2, dto.getName());
      assertEquals("High", dto.getDescription());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertSeverityResponseDto dto = new AlertSeverityResponseDto();

      dto.setSeverity_id(3);
      dto.setName(3);
      dto.setDescription("Medium");

      assertEquals(3, dto.getSeverity_id());
      assertEquals(3, dto.getName());
      assertEquals("Medium", dto.getDescription());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertSeverityResponseDto dto1 = new AlertSeverityResponseDto(1, 1, "Critical");
      AlertSeverityResponseDto dto2 = new AlertSeverityResponseDto(1, 1, "Critical");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertNotificationChannelResponseDto Tests
  @Nested
  class TestAlertNotificationChannelResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertNotificationChannelResponseDto dto = new AlertNotificationChannelResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertNotificationChannelResponseDto dto = new AlertNotificationChannelResponseDto(1, "Slack", "http://slack.webhook");

      assertEquals(1, dto.getNotification_channel_id());
      assertEquals("Slack", dto.getName());
      assertEquals("http://slack.webhook", dto.getNotification_webhook_url());
    }

    @Test
    void shouldCreateWithBuilder() {
      AlertNotificationChannelResponseDto dto = AlertNotificationChannelResponseDto.builder()
          .notification_channel_id(2)
          .name("Email")
          .notification_webhook_url("http://email.webhook")
          .build();

      assertEquals(2, dto.getNotification_channel_id());
      assertEquals("Email", dto.getName());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertNotificationChannelResponseDto dto = new AlertNotificationChannelResponseDto();

      dto.setNotification_channel_id(3);
      dto.setName("PagerDuty");
      dto.setNotification_webhook_url("http://pagerduty.webhook");

      assertEquals(3, dto.getNotification_channel_id());
      assertEquals("PagerDuty", dto.getName());
      assertEquals("http://pagerduty.webhook", dto.getNotification_webhook_url());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertNotificationChannelResponseDto dto1 = new AlertNotificationChannelResponseDto(1, "Slack", "url");
      AlertNotificationChannelResponseDto dto2 = new AlertNotificationChannelResponseDto(1, "Slack", "url");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertTagsResponseDto Tests
  @Nested
  class TestAlertTagsResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertTagsResponseDto dto = new AlertTagsResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertTagsResponseDto dto = new AlertTagsResponseDto(1, "production");

      assertEquals(1, dto.getTag_id());
      assertEquals("production", dto.getName());
    }

    @Test
    void shouldCreateWithBuilder() {
      AlertTagsResponseDto dto = AlertTagsResponseDto.builder()
          .tag_id(2)
          .name("critical")
          .build();

      assertEquals(2, dto.getTag_id());
      assertEquals("critical", dto.getName());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertTagsResponseDto dto = new AlertTagsResponseDto();

      dto.setTag_id(3);
      dto.setName("development");

      assertEquals(3, dto.getTag_id());
      assertEquals("development", dto.getName());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertTagsResponseDto dto1 = new AlertTagsResponseDto(1, "prod");
      AlertTagsResponseDto dto2 = new AlertTagsResponseDto(1, "prod");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertFiltersResponseDto Tests
  @Nested
  class TestAlertFiltersResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertFiltersResponseDto dto = new AlertFiltersResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<String> createdBy = List.of("user1", "user2");
      List<String> updatedBy = List.of("user3");
      List<AlertState> states = List.of(AlertState.NORMAL, AlertState.FIRING);

      AlertFiltersResponseDto dto = new AlertFiltersResponseDto(createdBy, updatedBy, states);

      assertEquals(createdBy, dto.getCreatedBy());
      assertEquals(updatedBy, dto.getUpdatedBy());
      assertEquals(states, dto.getCurrentState());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<String> createdBy = new ArrayList<>();
      List<String> updatedBy = new ArrayList<>();
      List<AlertState> states = new ArrayList<>();

      AlertFiltersResponseDto dto = AlertFiltersResponseDto.builder()
          .createdBy(createdBy)
          .updatedBy(updatedBy)
          .currentState(states)
          .build();

      assertEquals(createdBy, dto.createdBy);
      assertEquals(updatedBy, dto.updatedBy);
      assertEquals(states, dto.currentState);
    }

    @Test
    void shouldSetAndGetFields() {
      AlertFiltersResponseDto dto = new AlertFiltersResponseDto();

      List<String> createdBy = List.of("creator");
      List<String> updatedBy = List.of("updater");
      List<AlertState> states = List.of(AlertState.SILENCED);

      dto.setCreatedBy(createdBy);
      dto.setUpdatedBy(updatedBy);
      dto.setCurrentState(states);

      assertEquals(createdBy, dto.getCreatedBy());
      assertEquals(updatedBy, dto.getUpdatedBy());
      assertEquals(states, dto.getCurrentState());
    }
  }

  // AlertScopesResponseDto Tests
  @Nested
  class TestAlertScopesResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertScopesResponseDto dto = new AlertScopesResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertScopeItemDto> scopes = new ArrayList<>();
      AlertScopesResponseDto dto = new AlertScopesResponseDto(scopes);

      assertEquals(scopes, dto.getScopes());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<AlertScopeItemDto> scopes = new ArrayList<>();
      AlertScopesResponseDto dto = AlertScopesResponseDto.builder()
          .scopes(scopes)
          .build();

      assertEquals(scopes, dto.getScopes());
    }

    @Test
    void shouldSetAndGetScopes() {
      AlertScopesResponseDto dto = new AlertScopesResponseDto();
      List<AlertScopeItemDto> scopes = new ArrayList<>();

      dto.setScopes(scopes);

      assertEquals(scopes, dto.getScopes());
    }
  }

  // AlertScopeItemDto Tests
  @Nested
  class TestAlertScopeItemDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertScopeItemDto dto = new AlertScopeItemDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertScopeItemDto dto = new AlertScopeItemDto(1, "Interaction", "Interactions");

      assertEquals(1, dto.getId());
      assertEquals("Interaction", dto.getName());
      assertEquals("Interactions", dto.getLabel());
    }

    @Test
    void shouldCreateWithBuilder() {
      AlertScopeItemDto dto = AlertScopeItemDto.builder()
          .id(2)
          .name("network")
          .label("Network Requests")
          .build();

      assertEquals(2, dto.getId());
      assertEquals("network", dto.getName());
      assertEquals("Network Requests", dto.getLabel());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertScopeItemDto dto = new AlertScopeItemDto();

      dto.setId(3);
      dto.setName("screen");
      dto.setLabel("Screens");

      assertEquals(3, dto.getId());
      assertEquals("screen", dto.getName());
      assertEquals("Screens", dto.getLabel());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertScopeItemDto dto1 = new AlertScopeItemDto(1, "test", "Test");
      AlertScopeItemDto dto2 = new AlertScopeItemDto(1, "test", "Test");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertMetricsResponseDto Tests
  @Nested
  class TestAlertMetricsResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertMetricsResponseDto dto = new AlertMetricsResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<MetricItemDto> metrics = new ArrayList<>();
      AlertMetricsResponseDto dto = new AlertMetricsResponseDto("Interaction", metrics);

      assertEquals("Interaction", dto.getScope());
      assertEquals(metrics, dto.getMetrics());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<MetricItemDto> metrics = new ArrayList<>();
      AlertMetricsResponseDto dto = AlertMetricsResponseDto.builder()
          .scope("network")
          .metrics(metrics)
          .build();

      assertEquals("network", dto.getScope());
      assertEquals(metrics, dto.getMetrics());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertMetricsResponseDto dto = new AlertMetricsResponseDto();
      List<MetricItemDto> metrics = new ArrayList<>();

      dto.setScope("screen");
      dto.setMetrics(metrics);

      assertEquals("screen", dto.getScope());
      assertEquals(metrics, dto.getMetrics());
    }
  }

  // MetricItemDto Tests
  @Nested
  class TestMetricItemDto {

    @Test
    void shouldCreateWithNoArgs() {
      MetricItemDto dto = new MetricItemDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      MetricItemDto dto = new MetricItemDto(1, "error_rate", "Error Rate");

      assertEquals(1, dto.getId());
      assertEquals("error_rate", dto.getName());
      assertEquals("Error Rate", dto.getLabel());
    }

    @Test
    void shouldCreateWithBuilder() {
      MetricItemDto dto = MetricItemDto.builder()
          .id(2)
          .name("crash_rate")
          .label("Crash Rate")
          .build();

      assertEquals(2, dto.getId());
      assertEquals("crash_rate", dto.getName());
      assertEquals("Crash Rate", dto.getLabel());
    }

    @Test
    void shouldSetAndGetFields() {
      MetricItemDto dto = new MetricItemDto();

      dto.setId(3);
      dto.setName("anr_rate");
      dto.setLabel("ANR Rate");

      assertEquals(3, dto.getId());
      assertEquals("anr_rate", dto.getName());
      assertEquals("ANR Rate", dto.getLabel());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      MetricItemDto dto1 = new MetricItemDto(1, "test", "Test");
      MetricItemDto dto2 = new MetricItemDto(1, "test", "Test");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // ScopeEvaluationHistoryDto Tests
  @Nested
  class TestScopeEvaluationHistoryDto {

    @Test
    void shouldCreateWithNoArgs() {
      ScopeEvaluationHistoryDto dto = new ScopeEvaluationHistoryDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<EvaluationHistoryEntryDto> history = new ArrayList<>();
      ScopeEvaluationHistoryDto dto = new ScopeEvaluationHistoryDto(1, "home_screen", history);

      assertEquals(1, dto.getScopeId());
      assertEquals("home_screen", dto.getScopeName());
      assertEquals(history, dto.getEvaluationHistory());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<EvaluationHistoryEntryDto> history = new ArrayList<>();
      ScopeEvaluationHistoryDto dto = ScopeEvaluationHistoryDto.builder()
          .scopeId(2)
          .scopeName("login_screen")
          .evaluationHistory(history)
          .build();

      assertEquals(2, dto.getScopeId());
      assertEquals("login_screen", dto.getScopeName());
    }

    @Test
    void shouldSetAndGetFields() {
      ScopeEvaluationHistoryDto dto = new ScopeEvaluationHistoryDto();
      List<EvaluationHistoryEntryDto> history = new ArrayList<>();

      dto.setScopeId(3);
      dto.setScopeName("checkout_screen");
      dto.setEvaluationHistory(history);

      assertEquals(3, dto.getScopeId());
      assertEquals("checkout_screen", dto.getScopeName());
      assertEquals(history, dto.getEvaluationHistory());
    }
  }

  // EvaluationHistoryEntryDto Tests
  @Nested
  class TestEvaluationHistoryEntryDto {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluationHistoryEntryDto dto = new EvaluationHistoryEntryDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      EvaluationHistoryEntryDto dto = new EvaluationHistoryEntryDto(1, "result", AlertState.NORMAL, now);

      assertEquals(1, dto.getEvaluationId());
      assertEquals("result", dto.getEvaluationResult());
      assertEquals(AlertState.NORMAL, dto.getState());
      assertEquals(now, dto.getEvaluatedAt());
    }

    @Test
    void shouldCreateWithBuilder() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      EvaluationHistoryEntryDto dto = EvaluationHistoryEntryDto.builder()
          .evaluationId(2)
          .evaluationResult("passed")
          .state(AlertState.FIRING)
          .evaluatedAt(now)
          .build();

      assertEquals(2, dto.getEvaluationId());
      assertEquals("passed", dto.getEvaluationResult());
      assertEquals(AlertState.FIRING, dto.getState());
    }

    @Test
    void shouldSetAndGetFields() {
      EvaluationHistoryEntryDto dto = new EvaluationHistoryEntryDto();
      Timestamp now = new Timestamp(System.currentTimeMillis());

      dto.setEvaluationId(3);
      dto.setEvaluationResult("failed");
      dto.setState(AlertState.ERRORED);
      dto.setEvaluatedAt(now);

      assertEquals(3, dto.getEvaluationId());
      assertEquals("failed", dto.getEvaluationResult());
      assertEquals(AlertState.ERRORED, dto.getState());
      assertEquals(now, dto.getEvaluatedAt());
    }
  }

  // AlertEvaluationHistoryResponseDto Tests
  @Nested
  class TestAlertEvaluationHistoryResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertEvaluationHistoryResponseDto dto = new AlertEvaluationHistoryResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithBuilder() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      AlertEvaluationHistoryResponseDto dto = AlertEvaluationHistoryResponseDto.builder()
          .reading("0.05")
          .successInteractionCount(100)
          .errorInteractionCount(5)
          .totalInteractionCount(105)
          .evaluationTime(1.5f)
          .evaluatedAt(now)
          .currentState(AlertState.NORMAL)
          .minSuccessInteractions(10)
          .minErrorInteractions(1)
          .minTotalInteractions(11)
          .threshold(0.1f)
          .build();

      assertEquals("0.05", dto.getReading());
      assertEquals(100, dto.getSuccessInteractionCount());
      assertEquals(5, dto.getErrorInteractionCount());
      assertEquals(105, dto.getTotalInteractionCount());
      assertEquals(AlertState.NORMAL, dto.getCurrentState());
      assertEquals(0.1f, dto.getThreshold());
    }

    @Test
    void shouldSetAndGetAllFields() {
      AlertEvaluationHistoryResponseDto dto = new AlertEvaluationHistoryResponseDto();
      Timestamp now = new Timestamp(System.currentTimeMillis());

      dto.setReading("0.1");
      dto.setSuccessInteractionCount(200);
      dto.setErrorInteractionCount(10);
      dto.setTotalInteractionCount(210);
      dto.setEvaluationTime(2.0f);
      dto.setEvaluatedAt(now);
      dto.setCurrentState(AlertState.FIRING);
      dto.setMinSuccessInteractions(20);
      dto.setMinErrorInteractions(2);
      dto.setMinTotalInteractions(22);
      dto.setThreshold(0.05f);

      assertEquals("0.1", dto.getReading());
      assertEquals(200, dto.getSuccessInteractionCount());
      assertEquals(10, dto.getErrorInteractionCount());
      assertEquals(210, dto.getTotalInteractionCount());
      assertEquals(2.0f, dto.getEvaluationTime());
      assertEquals(AlertState.FIRING, dto.getCurrentState());
      assertEquals(0.05f, dto.getThreshold());
    }
  }

  // GetAlertsListRequestDto Tests
  @Nested
  class TestGetAlertsListRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      GetAlertsListRequestDto dto = new GetAlertsListRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      GetAlertsListRequestDto dto = new GetAlertsListRequestDto("Test", "Interaction", "user1", "user2", 10, 0);

      assertEquals("Test", dto.getName());
      assertEquals("Interaction", dto.getScope());
      assertEquals("user1", dto.getCreatedBy());
      assertEquals("user2", dto.getUpdatedBy());
      assertEquals(10, dto.getLimit());
      assertEquals(0, dto.getOffset());
    }

    @Test
    void shouldSetAndGetFields() {
      GetAlertsListRequestDto dto = new GetAlertsListRequestDto();

      dto.setName("Alert Name");
      dto.setScope("network");
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setLimit(20);
      dto.setOffset(5);

      assertEquals("Alert Name", dto.getName());
      assertEquals("network", dto.getScope());
      assertEquals("creator", dto.getCreatedBy());
      assertEquals("updater", dto.getUpdatedBy());
      assertEquals(20, dto.getLimit());
      assertEquals(5, dto.getOffset());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      GetAlertsListRequestDto dto1 = new GetAlertsListRequestDto("Test", "Interaction", "user", "user", 10, 0);
      GetAlertsListRequestDto dto2 = new GetAlertsListRequestDto("Test", "Interaction", "user", "user", 10, 0);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // CreateAlertRequestDto Tests
  @Nested
  class TestCreateAlertRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertRequestDto dto = new CreateAlertRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertConditionDto> alerts = new ArrayList<>();
      CreateAlertRequestDto dto = new CreateAlertRequestDto(
          "Test Alert", "Description", 60, 300, 1, 1, "user", "user",
          org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.Interaction,
          "{}", "A && B", alerts
      );

      assertEquals("Test Alert", dto.getName());
      assertEquals("Description", dto.getDescription());
      assertEquals(60, dto.getEvaluationPeriod());
      assertEquals(300, dto.getEvaluationInterval());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertRequestDto dto = new CreateAlertRequestDto();

      dto.setName("New Alert");
      dto.setDescription("New Description");
      dto.setEvaluationPeriod(120);
      dto.setEvaluationInterval(600);
      dto.setSeverity(2);
      dto.setNotificationChannelId(2);
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setScope(org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.network_api);
      dto.setDimensionFilters("{}");
      dto.setConditionExpression("A || B");
      dto.setAlerts(new ArrayList<>());

      assertEquals("New Alert", dto.getName());
      assertEquals(120, dto.getEvaluationPeriod());
      assertEquals(org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.network_api, dto.getScope());
    }
  }

  // UpdateAlertRequestDto Tests
  @Nested
  class TestUpdateAlertRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertConditionDto> alerts = new ArrayList<>();
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto(
          1, "Test Alert", "Description", 60, 300, 1, 1, "user", "user",
          org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.Interaction,
          "{}", "A && B", alerts
      );

      assertEquals(1, dto.getAlertId());
      assertEquals("Test Alert", dto.getName());
    }

    @Test
    void shouldSetAndGetFields() {
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto();

      dto.setAlertId(2);
      dto.setName("Updated Alert");
      dto.setDescription("Updated Description");
      dto.setEvaluationPeriod(180);
      dto.setEvaluationInterval(900);
      dto.setSeverity(3);
      dto.setNotificationChannelId(3);
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setScope(org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.screen);
      dto.setDimensionFilters("{}");
      dto.setConditionExpression("A && B && C");
      dto.setAlerts(new ArrayList<>());

      assertEquals(2, dto.getAlertId());
      assertEquals("Updated Alert", dto.getName());
      assertEquals(org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope.screen, dto.getScope());
    }
  }

  // CreateAlertSeverityRequestDto Tests
  @Nested
  class TestCreateAlertSeverityRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto(1, "Critical");

      assertEquals(1, dto.getName());
      assertEquals("Critical", dto.getDescription());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto();

      dto.setName(2);
      dto.setDescription("High");

      assertEquals(2, dto.getName());
      assertEquals("High", dto.getDescription());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertSeverityRequestDto dto1 = new CreateAlertSeverityRequestDto(1, "Critical");
      CreateAlertSeverityRequestDto dto2 = new CreateAlertSeverityRequestDto(1, "Critical");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // CreateAlertNotificationChannelRequestDto Tests
  @Nested
  class TestCreateAlertNotificationChannelRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto("Slack", "{\"webhook\":\"url\"}");

      assertEquals("Slack", dto.getName());
      assertEquals("{\"webhook\":\"url\"}", dto.getConfig());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto();

      dto.setName("Email");
      dto.setConfig("{\"email\":\"test@test.com\"}");

      assertEquals("Email", dto.getName());
      assertEquals("{\"email\":\"test@test.com\"}", dto.getConfig());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertNotificationChannelRequestDto dto1 = new CreateAlertNotificationChannelRequestDto("Slack", "{}");
      CreateAlertNotificationChannelRequestDto dto2 = new CreateAlertNotificationChannelRequestDto("Slack", "{}");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // CreateTagRequestDto Tests
  @Nested
  class TestCreateTagRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      CreateTagRequestDto dto = new CreateTagRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      CreateTagRequestDto dto = new CreateTagRequestDto("production");

      assertEquals("production", dto.getTag());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateTagRequestDto dto = new CreateTagRequestDto();

      dto.setTag("staging");

      assertEquals("staging", dto.getTag());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateTagRequestDto dto1 = new CreateTagRequestDto("prod");
      CreateTagRequestDto dto2 = new CreateTagRequestDto("prod");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // AlertTagMapRequestDto Tests
  @Nested
  class TestAlertTagMapRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto(1);

      assertEquals(1, dto.getTagId());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto();

      dto.setTagId(2);

      assertEquals(2, dto.getTagId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertTagMapRequestDto dto1 = new AlertTagMapRequestDto(1);
      AlertTagMapRequestDto dto2 = new AlertTagMapRequestDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // EvaluateAlertRequestDto Tests
  @Nested
  class TestEvaluateAlertRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto(1);

      assertEquals(1, dto.getAlertId());
    }

    @Test
    void shouldSetAndGetFields() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto();

      dto.setAlertId(2);

      assertEquals(2, dto.getAlertId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      EvaluateAlertRequestDto dto1 = new EvaluateAlertRequestDto(1);
      EvaluateAlertRequestDto dto2 = new EvaluateAlertRequestDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // EvaluateAlertResponseDto Tests
  @Nested
  class TestEvaluateAlertResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto();
      assertNotNull(dto);
    }
  }

  // AlertResponseDto Tests
  @Nested
  class TestAlertResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      AlertResponseDto dto = new AlertResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertResponseDto dto = new AlertResponseDto(1);

      assertEquals(1, dto.getAlert_id());
    }

    @Test
    void shouldCreateWithBuilder() {
      AlertResponseDto dto = AlertResponseDto.builder()
          .alert_id(2)
          .build();

      assertEquals(2, dto.getAlert_id());
    }

    @Test
    void shouldSetAndGetFields() {
      AlertResponseDto dto = new AlertResponseDto();

      dto.setAlert_id(3);

      assertEquals(3, dto.getAlert_id());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertResponseDto dto1 = new AlertResponseDto(1);
      AlertResponseDto dto2 = new AlertResponseDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // SnoozeAlertRestRequest Tests
  @Nested
  class TestSnoozeAlertRestRequest {

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestRequest dto = new SnoozeAlertRestRequest();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Long from = System.currentTimeMillis();
      Long until = from + 3600000;
      SnoozeAlertRestRequest dto = new SnoozeAlertRestRequest(from, until);

      assertEquals(from, dto.getSnoozeFrom());
      assertEquals(until, dto.getSnoozeUntil());
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestRequest dto = new SnoozeAlertRestRequest();

      dto.setSnoozeFrom(1000L);
      dto.setSnoozeUntil(2000L);

      assertEquals(1000L, dto.getSnoozeFrom());
      assertEquals(2000L, dto.getSnoozeUntil());
    }
  }

  // SnoozeAlertRestResponse Tests
  @Nested
  class TestSnoozeAlertRestResponse {

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestResponse dto = new SnoozeAlertRestResponse();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Long from = System.currentTimeMillis();
      Long until = from + 3600000;
      SnoozeAlertRestResponse dto = new SnoozeAlertRestResponse(true, from, until);

      assertTrue(dto.getIsSnoozed());
      assertEquals(from, dto.getSnoozedFrom());
      assertEquals(until, dto.getSnoozedUntil());
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestResponse dto = new SnoozeAlertRestResponse();

      dto.setIsSnoozed(false);
      dto.setSnoozedFrom(1000L);
      dto.setSnoozedUntil(2000L);

      assertEquals(false, dto.getIsSnoozed());
      assertEquals(1000L, dto.getSnoozedFrom());
      assertEquals(2000L, dto.getSnoozedUntil());
    }
  }

  // AddAlertToCronManager Tests
  @Nested
  class TestAddAlertToCronManager {

    @Test
    void shouldCreateWithNoArgs() {
      AddAlertToCronManager dto = new AddAlertToCronManager();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AddAlertToCronManager dto = new AddAlertToCronManager(1, 300, "http://localhost/api");

      assertEquals(1, dto.getId());
      assertEquals(300, dto.getInterval());
      assertEquals("http://localhost/api", dto.getUrl());
    }

    @Test
    void shouldSetAndGetFields() {
      AddAlertToCronManager dto = new AddAlertToCronManager();

      dto.setId(2);
      dto.setInterval(600);
      dto.setUrl("http://new.url/api");

      assertEquals(2, dto.getId());
      assertEquals(600, dto.getInterval());
      assertEquals("http://new.url/api", dto.getUrl());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AddAlertToCronManager dto1 = new AddAlertToCronManager(1, 300, "url");
      AddAlertToCronManager dto2 = new AddAlertToCronManager(1, 300, "url");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AddAlertToCronManager dto = new AddAlertToCronManager(1, 300, "url");
      assertNotNull(dto.toString());
      assertTrue(dto.toString().contains("1"));
    }
  }

  // DeleteAlertFromCronManager Tests
  @Nested
  class TestDeleteAlertFromCronManager {

    @Test
    void shouldCreateWithNoArgs() {
      DeleteAlertFromCronManager dto = new DeleteAlertFromCronManager();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      DeleteAlertFromCronManager dto = new DeleteAlertFromCronManager(1, 300);

      assertEquals(1, dto.getId());
      assertEquals(300, dto.getInterval());
    }

    @Test
    void shouldSetAndGetFields() {
      DeleteAlertFromCronManager dto = new DeleteAlertFromCronManager();

      dto.setId(2);
      dto.setInterval(600);

      assertEquals(2, dto.getId());
      assertEquals(600, dto.getInterval());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      DeleteAlertFromCronManager dto1 = new DeleteAlertFromCronManager(1, 300);
      DeleteAlertFromCronManager dto2 = new DeleteAlertFromCronManager(1, 300);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      DeleteAlertFromCronManager dto = new DeleteAlertFromCronManager(1, 300);
      assertNotNull(dto.toString());
      assertTrue(dto.toString().contains("1"));
    }
  }

  // UpdateAlertInCronManager Tests
  @Nested
  class TestUpdateAlertInCronManager {

    @Test
    void shouldCreateWithNoArgs() {
      UpdateAlertInCronManager dto = new UpdateAlertInCronManager();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      UpdateAlertInCronManager dto = new UpdateAlertInCronManager(1, 600, 300, "http://localhost/api");

      assertEquals(1, dto.getId());
      assertEquals(600, dto.getNewInterval());
      assertEquals(300, dto.getOldInterval());
      assertEquals("http://localhost/api", dto.getUrl());
    }

    @Test
    void shouldSetAndGetFields() {
      UpdateAlertInCronManager dto = new UpdateAlertInCronManager();

      dto.setId(2);
      dto.setNewInterval(900);
      dto.setOldInterval(600);
      dto.setUrl("http://new.url/api");

      assertEquals(2, dto.getId());
      assertEquals(900, dto.getNewInterval());
      assertEquals(600, dto.getOldInterval());
      assertEquals("http://new.url/api", dto.getUrl());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      UpdateAlertInCronManager dto1 = new UpdateAlertInCronManager(1, 600, 300, "url");
      UpdateAlertInCronManager dto2 = new UpdateAlertInCronManager(1, 600, 300, "url");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      UpdateAlertInCronManager dto = new UpdateAlertInCronManager(1, 600, 300, "url");
      assertNotNull(dto.toString());
      assertTrue(dto.toString().contains("1"));
    }
  }

  // EvaluateAndTriggerAlertRequestDto Tests
  @Nested
  class TestEvaluateAndTriggerAlertRequestDto {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAndTriggerAlertRequestDto dto = new EvaluateAndTriggerAlertRequestDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      EvaluateAndTriggerAlertRequestDto dto = new EvaluateAndTriggerAlertRequestDto(1);

      assertEquals(1, dto.alertId);
    }

    @Test
    void shouldSetAndGetFields() {
      EvaluateAndTriggerAlertRequestDto dto = new EvaluateAndTriggerAlertRequestDto();

      dto.setAlertId(2);

      assertEquals(2, dto.getAlertId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      EvaluateAndTriggerAlertRequestDto dto1 = new EvaluateAndTriggerAlertRequestDto(1);
      EvaluateAndTriggerAlertRequestDto dto2 = new EvaluateAndTriggerAlertRequestDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  // EvaluateAndTriggerAlertResponseDto Tests
  @Nested
  class TestEvaluateAndTriggerAlertResponseDto {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAndTriggerAlertResponseDto dto = new EvaluateAndTriggerAlertResponseDto();
      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      EvaluateAndTriggerAlertResponseDto dto = new EvaluateAndTriggerAlertResponseDto("1", "query-123");

      assertEquals("1", dto.getAlertId());
      assertEquals("query-123", dto.getQueryId());
    }

    @Test
    void shouldCreateWithBuilder() {
      EvaluateAndTriggerAlertResponseDto dto = EvaluateAndTriggerAlertResponseDto.builder()
          .alertId("2")
          .queryId("query-456")
          .build();

      assertEquals("2", dto.getAlertId());
      assertEquals("query-456", dto.getQueryId());
    }

    @Test
    void shouldSetAndGetFields() {
      EvaluateAndTriggerAlertResponseDto dto = new EvaluateAndTriggerAlertResponseDto();

      dto.setAlertId("3");
      dto.setQueryId("query-789");

      assertEquals("3", dto.getAlertId());
      assertEquals("query-789", dto.getQueryId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      EvaluateAndTriggerAlertResponseDto dto1 = new EvaluateAndTriggerAlertResponseDto("1", "query-123");
      EvaluateAndTriggerAlertResponseDto dto2 = new EvaluateAndTriggerAlertResponseDto("1", "query-123");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }
}

