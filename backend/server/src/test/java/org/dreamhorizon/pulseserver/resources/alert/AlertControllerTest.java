package org.dreamhorizon.pulseserver.resources.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertConditionDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope;
import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
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
class AlertControllerTest {

  @Mock
  private AlertService alertService;

  private AlertController alertController;

  @BeforeEach
  void setUp() {
    alertController = new AlertController(alertService);
  }

  @Nested
  class TestAlertControllerInitialization {

    @Test
    void shouldInitializeAlertController() {
      assertNotNull(alertController);
    }
  }

  @Nested
  class TestAlertMapper {

    @Test
    void shouldHaveMapperInstance() {
      assertNotNull(AlertMapper.INSTANCE);
    }

    @Test
    void shouldConvertEpochToLocalDateTime() {
      AlertMapper mapper = AlertMapper.INSTANCE;
      Long epoch = System.currentTimeMillis();

      LocalDateTime result = mapper.epochToLocalDateTime(epoch);

      assertNotNull(result);
    }

    @Test
    void shouldReturnNullWhenEpochIsNull() {
      AlertMapper mapper = AlertMapper.INSTANCE;

      LocalDateTime result = mapper.epochToLocalDateTime(null);

      assertNull(result);
    }

    @Test
    void shouldConvertLocalDateTimeToEpoch() {
      AlertMapper mapper = AlertMapper.INSTANCE;
      LocalDateTime dateTime = LocalDateTime.now(ZoneOffset.UTC);

      Long result = mapper.localDateTimeToEpoch(dateTime);

      assertNotNull(result);
    }

    @Test
    void shouldReturnNullWhenLocalDateTimeIsNull() {
      AlertMapper mapper = AlertMapper.INSTANCE;

      Long result = mapper.localDateTimeToEpoch(null);

      assertNull(result);
    }

    @Test
    void shouldConvertSnoozeAlertRestRequestToServiceRequest() {
      AlertMapper mapper = AlertMapper.INSTANCE;
      Long now = System.currentTimeMillis();
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(now, now + 3600000);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertEquals(1, result.getAlertId());
      assertEquals("user@test.com", result.getUpdatedBy());
    }

    @Test
    void shouldHandleNullSnoozeTimesInRestRequest() {
      AlertMapper mapper = AlertMapper.INSTANCE;
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(null, null);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertNull(result.getSnoozeFrom());
      assertNull(result.getSnoozeUntil());
    }

    @Test
    void shouldConvertEpochCorrectly() {
      AlertMapper mapper = AlertMapper.INSTANCE;
      Long epoch = 1700000000000L;

      LocalDateTime result = mapper.epochToLocalDateTime(epoch);

      assertNotNull(result);
      Long convertedBack = mapper.localDateTimeToEpoch(result);
      assertEquals(epoch, convertedBack);
    }
  }

  @Nested
  class TestCreateAlertRequestDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertConditionDto> alerts = new ArrayList<>();
      CreateAlertRequestDto dto = new CreateAlertRequestDto(
          "Test Alert",
          "Description",
          60,
          300,
          1,
          1,
          "user",
          "user",
          AlertScope.Interaction,
          "{}",
          "A && B",
          alerts
      );

      assertEquals("Test Alert", dto.getName());
      assertEquals("Description", dto.getDescription());
      assertEquals(60, dto.getEvaluationPeriod());
      assertEquals(300, dto.getEvaluationInterval());
      assertEquals(1, dto.getSeverity());
      assertEquals(1, dto.getNotificationChannelId());
      assertEquals("user", dto.getCreatedBy());
      assertEquals("user", dto.getUpdatedBy());
      assertEquals(AlertScope.Interaction, dto.getScope());
      assertEquals("{}", dto.getDimensionFilters());
      assertEquals("A && B", dto.getConditionExpression());
      assertEquals(alerts, dto.getAlerts());
    }

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertRequestDto dto = new CreateAlertRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetAllFields() {
      CreateAlertRequestDto dto = new CreateAlertRequestDto();
      List<AlertConditionDto> alerts = new ArrayList<>();

      dto.setName("Updated Name");
      dto.setDescription("Updated Description");
      dto.setEvaluationPeriod(120);
      dto.setEvaluationInterval(600);
      dto.setSeverity(2);
      dto.setNotificationChannelId(2);
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setScope(AlertScope.network);
      dto.setDimensionFilters("{\"key\":\"value\"}");
      dto.setConditionExpression("A || B");
      dto.setAlerts(alerts);

      assertEquals("Updated Name", dto.getName());
      assertEquals("Updated Description", dto.getDescription());
      assertEquals(120, dto.getEvaluationPeriod());
      assertEquals(600, dto.getEvaluationInterval());
      assertEquals(2, dto.getSeverity());
      assertEquals(2, dto.getNotificationChannelId());
      assertEquals("creator", dto.getCreatedBy());
      assertEquals("updater", dto.getUpdatedBy());
      assertEquals(AlertScope.network, dto.getScope());
      assertEquals("{\"key\":\"value\"}", dto.getDimensionFilters());
      assertEquals("A || B", dto.getConditionExpression());
      assertEquals(alerts, dto.getAlerts());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertRequestDto dto1 = createSampleCreateAlertRequestDto();
      CreateAlertRequestDto dto2 = createSampleCreateAlertRequestDto();

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      CreateAlertRequestDto dto = createSampleCreateAlertRequestDto();

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("Test Alert"));
    }
  }

  @Nested
  class TestUpdateAlertRequestDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      List<AlertConditionDto> alerts = new ArrayList<>();
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto(
          1,
          "Test Alert",
          "Description",
          60,
          300,
          1,
          1,
          "user",
          "user",
          AlertScope.Interaction,
          "{}",
          "A && B",
          alerts
      );

      assertEquals(1, dto.getAlertId());
      assertEquals("Test Alert", dto.getName());
      assertEquals("Description", dto.getDescription());
      assertEquals(60, dto.getEvaluationPeriod());
      assertEquals(300, dto.getEvaluationInterval());
    }

    @Test
    void shouldCreateWithNoArgs() {
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetAllFields() {
      UpdateAlertRequestDto dto = new UpdateAlertRequestDto();

      dto.setAlertId(2);
      dto.setName("Updated Name");
      dto.setDescription("Updated Description");
      dto.setEvaluationPeriod(120);
      dto.setEvaluationInterval(600);
      dto.setSeverity(2);
      dto.setNotificationChannelId(2);
      dto.setCreatedBy("creator");
      dto.setUpdatedBy("updater");
      dto.setScope(AlertScope.screen);
      dto.setDimensionFilters("{\"key\":\"value\"}");
      dto.setConditionExpression("A || B");
      dto.setAlerts(new ArrayList<>());

      assertEquals(2, dto.getAlertId());
      assertEquals("Updated Name", dto.getName());
      assertEquals(AlertScope.screen, dto.getScope());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      UpdateAlertRequestDto dto1 = createSampleUpdateAlertRequestDto();
      UpdateAlertRequestDto dto2 = createSampleUpdateAlertRequestDto();

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      UpdateAlertRequestDto dto = createSampleUpdateAlertRequestDto();

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("Test Alert"));
    }
  }

  @Nested
  class TestAlertResponseDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      AlertResponseDto dto = new AlertResponseDto(1);

      assertEquals(1, dto.getAlert_id());
    }

    @Test
    void shouldCreateWithNoArgs() {
      AlertResponseDto dto = new AlertResponseDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetAlertId() {
      AlertResponseDto dto = new AlertResponseDto();
      dto.setAlert_id(2);

      assertEquals(2, dto.getAlert_id());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertResponseDto dto1 = new AlertResponseDto(1);
      AlertResponseDto dto2 = new AlertResponseDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertResponseDto dto = new AlertResponseDto(1);

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("1"));
    }

    @Test
    void shouldBuildWithBuilder() {
      AlertResponseDto dto = AlertResponseDto.builder()
          .alert_id(3)
          .build();

      assertEquals(3, dto.getAlert_id());
    }
  }

  @Nested
  class TestSnoozeAlertRestRequestModel {

    @Test
    void shouldCreateWithAllArgs() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);

      assertEquals(snoozeFrom, request.getSnoozeFrom());
      assertEquals(snoozeUntil, request.getSnoozeUntil());
    }

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest();

      assertNotNull(request);
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest();
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;

      request.setSnoozeFrom(snoozeFrom);
      request.setSnoozeUntil(snoozeUntil);

      assertEquals(snoozeFrom, request.getSnoozeFrom());
      assertEquals(snoozeUntil, request.getSnoozeUntil());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestRequest request1 = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);
      SnoozeAlertRestRequest request2 = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);

      assertEquals(request1, request2);
      assertEquals(request1.hashCode(), request2.hashCode());
    }
  }

  @Nested
  class TestSnoozeAlertRestResponseModel {

    @Test
    void shouldCreateWithAllArgs() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);

      assertTrue(response.getIsSnoozed());
      assertEquals(snoozeFrom, response.getSnoozedFrom());
      assertEquals(snoozeUntil, response.getSnoozedUntil());
    }

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse();

      assertNotNull(response);
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse();
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;

      response.setIsSnoozed(false);
      response.setSnoozedFrom(snoozeFrom);
      response.setSnoozedUntil(snoozeUntil);

      assertFalse(response.getIsSnoozed());
      assertEquals(snoozeFrom, response.getSnoozedFrom());
      assertEquals(snoozeUntil, response.getSnoozedUntil());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestResponse response1 = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);
      SnoozeAlertRestResponse response2 = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);

      assertEquals(response1, response2);
      assertEquals(response1.hashCode(), response2.hashCode());
    }
  }

  @Nested
  class TestAlertScopeEnum {

    @Test
    void shouldHaveAllExpectedValues() {
      AlertScope[] scopes = AlertScope.values();

      assertEquals(4, scopes.length);
      assertNotNull(AlertScope.valueOf("Interaction"));
      assertNotNull(AlertScope.valueOf("network"));
      assertNotNull(AlertScope.valueOf("screen"));
      assertNotNull(AlertScope.valueOf("app_vitals"));
    }
  }

  @Nested
  class TestAlertConditionDtoModel {

    @Test
    void shouldCreateAlertConditionDtoWithNoArgs() {
      AlertConditionDto dto = new AlertConditionDto();

      assertNotNull(dto);
    }

    @Test
    void shouldCreateAlertConditionDtoWithAllArgs() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("screen1", 0.05f);

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
      threshold.put("scope1", 0.1f);

      AlertConditionDto dto1 = new AlertConditionDto("A", Metric.ERROR_RATE, MetricOperator.GREATER_THAN, threshold);
      AlertConditionDto dto2 = new AlertConditionDto("A", Metric.ERROR_RATE, MetricOperator.GREATER_THAN, threshold);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AlertConditionDto dto = new AlertConditionDto();
      dto.setAlias("A");

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("A"));
    }
  }

  @Nested
  class TestMetricEnum {

    @Test
    void shouldHaveMetricValues() {
      Metric[] metrics = Metric.values();

      assertTrue(metrics.length > 0);
      assertNotNull(Metric.valueOf("ERROR_RATE"));
      assertNotNull(Metric.valueOf("CRASH_RATE"));
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
  }

  // Helper methods

  private CreateAlertRequestDto createSampleCreateAlertRequestDto() {
    return new CreateAlertRequestDto(
        "Test Alert",
        "Description",
        60,
        300,
        1,
        1,
        "user",
        "user",
        AlertScope.Interaction,
        null,
        "A && B",
        new ArrayList<>()
    );
  }

  private UpdateAlertRequestDto createSampleUpdateAlertRequestDto() {
    return new UpdateAlertRequestDto(
        1,
        "Test Alert",
        "Description",
        60,
        300,
        1,
        1,
        "user",
        "user",
        AlertScope.Interaction,
        null,
        "A && B",
        new ArrayList<>()
    );
  }
}
