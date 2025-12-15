package org.dreamhorizon.pulseserver.service.alert.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertConditionDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertModelsServiceTest {

  // Alert Tests
  @Nested
  class TestAlert {

    @Test
    void shouldCreateWithNoArgs() {
      Alert alert = new Alert();
      assertNotNull(alert);
    }

    @Test
    void shouldCreateWithBuilder() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      LocalDateTime nowLocal = LocalDateTime.now();
      List<AlertConditionDto> alerts = new ArrayList<>();

      Alert alert = Alert.builder()
          .alertId(1)
          .name("Test Alert")
          .description("Description")
          .scope("Interaction")
          .dimensionFilter("{}")
          .alerts(alerts)
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
          .lastSnoozedAt(nowLocal)
          .snoozedFrom(nowLocal)
          .snoozedUntil(nowLocal)
          .isSnoozed(false)
          .build();

      assertEquals(1, alert.getAlertId());
      assertEquals("Test Alert", alert.getName());
      assertEquals("Description", alert.getDescription());
      assertEquals("Interaction", alert.getScope());
      assertEquals("{}", alert.getDimensionFilter());
      assertEquals("A && B", alert.getConditionExpression());
      assertEquals(60, alert.getEvaluationPeriod());
      assertEquals(300, alert.getEvaluationInterval());
      assertEquals(1, alert.getSeverityId());
      assertEquals(1, alert.getNotificationChannelId());
      assertEquals("http://webhook.url", alert.getNotificationWebhookUrl());
      assertEquals("user", alert.getCreatedBy());
      assertEquals("user", alert.getUpdatedBy());
      assertEquals(now, alert.getCreatedAt());
      assertEquals(now, alert.getUpdatedAt());
      assertTrue(alert.getIsActive());
      assertEquals(nowLocal, alert.getLastSnoozedAt());
      assertEquals(nowLocal, alert.getSnoozedFrom());
      assertEquals(nowLocal, alert.getSnoozedUntil());
      assertEquals(false, alert.getIsSnoozed());
    }

    @Test
    void shouldUseToBuilder() {
      Alert original = Alert.builder()
          .alertId(1)
          .name("Original")
          .build();

      Alert modified = original.toBuilder()
          .name("Modified")
          .build();

      assertEquals("Modified", modified.getName());
      assertEquals(1, modified.getAlertId());
    }

    @Test
    void shouldHaveAllNullableFieldsAsNull() {
      Alert alert = Alert.builder().build();

      assertNull(alert.getAlertId());
      assertNull(alert.getName());
      assertNull(alert.getDimensionFilter());
      assertNull(alert.getLastSnoozedAt());
      assertNull(alert.getSnoozedFrom());
      assertNull(alert.getSnoozedUntil());
      assertNull(alert.getIsSnoozed());
    }
  }

  // AlertCondition Tests
  @Nested
  class TestAlertCondition {

    @Test
    void shouldCreateWithBuilder() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("default", 0.5f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.ERROR_RATE)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      assertEquals("A", condition.getAlias());
      assertEquals(Metric.ERROR_RATE, condition.getMetric());
      assertEquals(MetricOperator.GREATER_THAN, condition.getMetricOperator());
      assertEquals(threshold, condition.getThreshold());
    }

    @Test
    void shouldSetAndGetFields() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.1f);

      AlertCondition condition = AlertCondition.builder()
          .alias("B")
          .metric(Metric.CRASH_RATE)
          .metricOperator(MetricOperator.LESS_THAN)
          .threshold(threshold)
          .build();

      condition.setAlias("C");
      condition.setMetric(Metric.ANR_RATE);
      condition.setMetricOperator(MetricOperator.GREATER_THAN_EQUAL);

      assertEquals("C", condition.getAlias());
      assertEquals(Metric.ANR_RATE, condition.getMetric());
      assertEquals(MetricOperator.GREATER_THAN_EQUAL, condition.getMetricOperator());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Map<String, Float> threshold = new HashMap<>();

      AlertCondition c1 = AlertCondition.builder()
          .alias("A")
          .metric(Metric.ERROR_RATE)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      AlertCondition c2 = AlertCondition.builder()
          .alias("A")
          .metric(Metric.ERROR_RATE)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      assertEquals(c1, c2);
      assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .build();

      assertNotNull(condition.toString());
      assertTrue(condition.toString().contains("A"));
    }
  }

  // CreateAlertRequest Tests
  @Nested
  class TestCreateAlertRequest {

    @Test
    void shouldCreateWithBuilder() {
      List<AlertCondition> alerts = new ArrayList<>();

      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("New Alert")
          .description("Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("creator")
          .updatedBy("updater")
          .scope(AlertScope.Interaction)
          .dimensionFilters("{}")
          .conditionExpression("A && B")
          .alerts(alerts)
          .build();

      assertEquals("New Alert", request.getName());
      assertEquals("Description", request.getDescription());
      assertEquals(60, request.getEvaluationPeriod());
      assertEquals(300, request.getEvaluationInterval());
      assertEquals(1, request.getSeverity());
      assertEquals(1, request.getNotificationChannelId());
      assertEquals("creator", request.getCreatedBy());
      assertEquals("updater", request.getUpdatedBy());
      assertEquals(AlertScope.Interaction, request.getScope());
      assertEquals("{}", request.getDimensionFilters());
      assertEquals("A && B", request.getConditionExpression());
      assertEquals(alerts, request.getAlerts());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertRequest request = CreateAlertRequest.builder().build();

      request.setName("Updated Name");
      request.setDescription("Updated Description");
      request.setEvaluationPeriod(120);
      request.setEvaluationInterval(600);
      request.setSeverity(2);
      request.setNotificationChannelId(2);
      request.setCreatedBy("newCreator");
      request.setUpdatedBy("newUpdater");
      request.setScope(AlertScope.network);
      request.setDimensionFilters("{}");
      request.setConditionExpression("A || B");
      request.setAlerts(new ArrayList<>());

      assertEquals("Updated Name", request.getName());
      assertEquals(AlertScope.network, request.getScope());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertRequest r1 = CreateAlertRequest.builder()
          .name("Alert")
          .severity(1)
          .build();

      CreateAlertRequest r2 = CreateAlertRequest.builder()
          .name("Alert")
          .severity(1)
          .build();

      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }
  }

  // UpdateAlertRequest Tests
  @Nested
  class TestUpdateAlertRequest {

    @Test
    void shouldCreateWithBuilder() {
      List<AlertCondition> alerts = new ArrayList<>();

      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("creator")
          .updatedBy("updater")
          .scope(AlertScope.Interaction)
          .dimensionFilters("{}")
          .conditionExpression("A && B")
          .alerts(alerts)
          .build();

      assertEquals(1, request.getAlertId());
      assertEquals("Updated Alert", request.getName());
      assertEquals("Description", request.getDescription());
      assertEquals(60, request.getEvaluationPeriod());
      assertEquals(300, request.getEvaluationInterval());
      assertEquals(1, request.getSeverity());
      assertEquals(1, request.getNotificationChannelId());
      assertEquals("creator", request.getCreatedBy());
      assertEquals("updater", request.getUpdatedBy());
      assertEquals(AlertScope.Interaction, request.getScope());
      assertEquals("{}", request.getDimensionFilters());
      assertEquals("A && B", request.getConditionExpression());
    }

    @Test
    void shouldUseToBuilder() {
      UpdateAlertRequest original = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Original")
          .build();

      UpdateAlertRequest modified = original.toBuilder()
          .name("Modified")
          .build();

      assertEquals("Modified", modified.getName());
      assertEquals(1, modified.getAlertId());
    }
  }

  // GetAlertsResponse Tests
  @Nested
  class TestGetAlertsResponse {

    @Test
    void shouldCreateWithNoArgs() {
      GetAlertsResponse response = new GetAlertsResponse();
      assertNotNull(response);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<Alert> alerts = new ArrayList<>();
      GetAlertsResponse response = new GetAlertsResponse(10, alerts, 0, 5);

      assertEquals(10, response.getTotalAlerts());
      assertEquals(alerts, response.getAlerts());
      assertEquals(0, response.getPage());
      assertEquals(5, response.getLimit());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<Alert> alerts = new ArrayList<>();
      GetAlertsResponse response = GetAlertsResponse.builder()
          .totalAlerts(20)
          .alerts(alerts)
          .page(1)
          .limit(10)
          .build();

      assertEquals(20, response.getTotalAlerts());
      assertEquals(1, response.getPage());
      assertEquals(10, response.getLimit());
    }

    @Test
    void shouldSetAndGetFields() {
      GetAlertsResponse response = new GetAlertsResponse();
      List<Alert> alerts = new ArrayList<>();

      response.setTotalAlerts(15);
      response.setAlerts(alerts);
      response.setPage(2);
      response.setLimit(20);

      assertEquals(15, response.getTotalAlerts());
      assertEquals(alerts, response.getAlerts());
      assertEquals(2, response.getPage());
      assertEquals(20, response.getLimit());
    }
  }

  // GetAllAlertsResponse Tests
  @Nested
  class TestGetAllAlertsResponse {

    @Test
    void shouldCreateWithNoArgs() {
      GetAllAlertsResponse response = new GetAllAlertsResponse();
      assertNotNull(response);
    }

    @Test
    void shouldCreateWithAllArgs() {
      List<Alert> alerts = new ArrayList<>();
      GetAllAlertsResponse response = new GetAllAlertsResponse(alerts);

      assertEquals(alerts, response.getAlerts());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<Alert> alerts = new ArrayList<>();
      GetAllAlertsResponse response = GetAllAlertsResponse.builder()
          .alerts(alerts)
          .build();

      assertEquals(alerts, response.getAlerts());
    }

    @Test
    void shouldSetAndGetAlerts() {
      GetAllAlertsResponse response = new GetAllAlertsResponse();
      List<Alert> alerts = new ArrayList<>();

      response.setAlerts(alerts);

      assertEquals(alerts, response.getAlerts());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      List<Alert> alerts = new ArrayList<>();
      GetAllAlertsResponse r1 = new GetAllAlertsResponse(alerts);
      GetAllAlertsResponse r2 = new GetAllAlertsResponse(alerts);

      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }
  }

  // AlertScope Enum Tests
  @Nested
  class TestAlertScope {

    @Test
    void shouldHaveAllExpectedValues() {
      AlertScope[] scopes = AlertScope.values();
      assertNotNull(scopes);
      assertTrue(scopes.length > 0);
    }

    @Test
    void shouldHaveInteractionScope() {
      AlertScope scope = AlertScope.Interaction;
      assertNotNull(scope);
      assertEquals("Interaction", scope.name());
    }

    @Test
    void shouldHaveNetworkScope() {
      AlertScope scope = AlertScope.network;
      assertNotNull(scope);
    }

    @Test
    void shouldHaveScreenScope() {
      AlertScope scope = AlertScope.screen;
      assertNotNull(scope);
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(AlertScope.Interaction, AlertScope.valueOf("Interaction"));
      assertEquals(AlertScope.network, AlertScope.valueOf("network"));
      assertEquals(AlertScope.screen, AlertScope.valueOf("screen"));
    }
  }

  // Metric Enum Tests
  @Nested
  class TestMetric {

    @Test
    void shouldHaveAllExpectedValues() {
      Metric[] metrics = Metric.values();
      assertNotNull(metrics);
      assertTrue(metrics.length > 0);
    }

    @Test
    void shouldHaveErrorRateMetric() {
      Metric metric = Metric.ERROR_RATE;
      assertNotNull(metric);
      assertEquals("ERROR_RATE", metric.name());
    }

    @Test
    void shouldHaveCrashRateMetric() {
      Metric metric = Metric.CRASH_RATE;
      assertNotNull(metric);
      assertEquals("CRASH_RATE", metric.name());
    }

    @Test
    void shouldHaveAnrRateMetric() {
      Metric metric = Metric.ANR_RATE;
      assertNotNull(metric);
      assertEquals("ANR_RATE", metric.name());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(Metric.ERROR_RATE, Metric.valueOf("ERROR_RATE"));
      assertEquals(Metric.CRASH_RATE, Metric.valueOf("CRASH_RATE"));
      assertEquals(Metric.ANR_RATE, Metric.valueOf("ANR_RATE"));
    }

    @Test
    void shouldHaveAllMetricsWithValidNames() {
      for (Metric metric : Metric.values()) {
        assertNotNull(metric.name());
        assertNotNull(metric.toString());
      }
    }
  }

  // MetricOperator Enum Tests
  @Nested
  class TestMetricOperator {

    @Test
    void shouldHaveAllExpectedValues() {
      MetricOperator[] operators = MetricOperator.values();
      assertEquals(4, operators.length);
    }

    @Test
    void shouldHaveGreaterThan() {
      MetricOperator op = MetricOperator.GREATER_THAN;
      assertNotNull(op);
    }

    @Test
    void shouldHaveLessThan() {
      MetricOperator op = MetricOperator.LESS_THAN;
      assertNotNull(op);
    }

    @Test
    void shouldHaveGreaterThanEqual() {
      MetricOperator op = MetricOperator.GREATER_THAN_EQUAL;
      assertNotNull(op);
    }

    @Test
    void shouldHaveLessThanEqual() {
      MetricOperator op = MetricOperator.LESS_THAN_EQUAL;
      assertNotNull(op);
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(MetricOperator.GREATER_THAN, MetricOperator.valueOf("GREATER_THAN"));
      assertEquals(MetricOperator.LESS_THAN, MetricOperator.valueOf("LESS_THAN"));
      assertEquals(MetricOperator.GREATER_THAN_EQUAL, MetricOperator.valueOf("GREATER_THAN_EQUAL"));
      assertEquals(MetricOperator.LESS_THAN_EQUAL, MetricOperator.valueOf("LESS_THAN_EQUAL"));
    }
  }

  // SnoozeAlertRequest Tests
  @Nested
  class TestSnoozeAlertRequest {

    @Test
    void shouldCreateWithBuilder() {
      LocalDateTime from = LocalDateTime.now();
      LocalDateTime until = from.plusHours(1);

      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(1)
          .snoozeFrom(from)
          .snoozeUntil(until)
          .updatedBy("user")
          .build();

      assertEquals(1, request.getAlertId());
      assertEquals(from, request.getSnoozeFrom());
      assertEquals(until, request.getSnoozeUntil());
      assertEquals("user", request.getUpdatedBy());
    }

    @Test
    void shouldHaveAllGetters() {
      LocalDateTime from = LocalDateTime.now();
      LocalDateTime until = from.plusHours(2);

      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(2)
          .snoozeFrom(from)
          .snoozeUntil(until)
          .updatedBy("admin")
          .build();

      assertEquals(2, request.getAlertId());
      assertEquals(from, request.getSnoozeFrom());
      assertEquals(until, request.getSnoozeUntil());
      assertEquals("admin", request.getUpdatedBy());
    }

    @Test
    void shouldCreateWithNullableFields() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(1)
          .build();

      assertEquals(1, request.getAlertId());
      assertNull(request.getSnoozeFrom());
      assertNull(request.getSnoozeUntil());
    }
  }

  // SnoozeAlertResponse Tests
  @Nested
  class TestSnoozeAlertResponse {

    @Test
    void shouldCreateWithBuilder() {
      LocalDateTime from = LocalDateTime.now();
      LocalDateTime until = from.plusHours(1);

      SnoozeAlertResponse response = SnoozeAlertResponse.builder()
          .isSnoozed(true)
          .snoozedFrom(from)
          .snoozedUntil(until)
          .build();

      assertTrue(response.getIsSnoozed());
      assertEquals(from, response.getSnoozedFrom());
      assertEquals(until, response.getSnoozedUntil());
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertResponse response = SnoozeAlertResponse.builder().build();
      LocalDateTime from = LocalDateTime.now();
      LocalDateTime until = from.plusHours(2);

      response.setIsSnoozed(false);
      response.setSnoozedFrom(from);
      response.setSnoozedUntil(until);

      assertEquals(false, response.getIsSnoozed());
      assertEquals(from, response.getSnoozedFrom());
      assertEquals(until, response.getSnoozedUntil());
    }
  }

  // DeleteSnoozeRequest Tests
  @Nested
  class TestDeleteSnoozeRequest {

    @Test
    void shouldCreateWithBuilder() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(1)
          .updatedBy("user")
          .build();

      assertEquals(1, request.getAlertId());
      assertEquals("user", request.getUpdatedBy());
    }
  }

  // GenericSuccessResponse Tests
  @Nested
  class TestGenericSuccessResponse {

    @Test
    void shouldCreateWithNoArgs() {
      GenericSuccessResponse response = new GenericSuccessResponse();
      assertNotNull(response);
    }

    @Test
    void shouldCreateWithAllArgs() {
      GenericSuccessResponse response = new GenericSuccessResponse("success");

      assertEquals("success", response.getStatus());
    }

    @Test
    void shouldCreateWithBuilder() {
      GenericSuccessResponse response = GenericSuccessResponse.builder()
          .status("completed")
          .build();

      assertEquals("completed", response.getStatus());
    }

    @Test
    void shouldSetAndGetFields() {
      GenericSuccessResponse response = new GenericSuccessResponse();

      response.setStatus("failed");

      assertEquals("failed", response.getStatus());
    }

    @Test
    void shouldHaveCorrectToString() {
      GenericSuccessResponse response = new GenericSuccessResponse("ok");
      assertNotNull(response.toString());
    }
  }
}

