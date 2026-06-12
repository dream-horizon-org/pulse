package org.dreamhorizon.pulseserver.resources.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertMapperTest {

  private final AlertMapper mapper = AlertMapper.INSTANCE;

  @Nested
  class TestMapperInstance {

    @Test
    void shouldHaveMapperInstance() {
      assertNotNull(AlertMapper.INSTANCE);
    }

    @Test
    void shouldReturnSameInstance() {
      AlertMapper instance1 = AlertMapper.INSTANCE;
      AlertMapper instance2 = AlertMapper.INSTANCE;

      assertEquals(instance1, instance2);
    }
  }

  @Nested
  class TestEpochToLocalDateTime {

    @Test
    void shouldConvertEpochToLocalDateTime() {
      Long epoch = 1700000000000L;

      LocalDateTime result = mapper.epochToLocalDateTime(epoch);

      assertNotNull(result);
      assertEquals(2023, result.getYear());
    }

    @Test
    void shouldReturnNullWhenEpochIsNull() {
      LocalDateTime result = mapper.epochToLocalDateTime(null);

      assertNull(result);
    }

    @Test
    void shouldConvertCurrentTimeEpoch() {
      Long epoch = System.currentTimeMillis();

      LocalDateTime result = mapper.epochToLocalDateTime(epoch);

      assertNotNull(result);
    }

    @Test
    void shouldConvertZeroEpoch() {
      Long epoch = 0L;

      LocalDateTime result = mapper.epochToLocalDateTime(epoch);

      assertNotNull(result);
      assertEquals(1970, result.getYear());
    }
  }

  @Nested
  class TestLocalDateTimeToEpoch {

    @Test
    void shouldConvertLocalDateTimeToEpoch() {
      LocalDateTime dateTime = LocalDateTime.of(2023, 11, 14, 22, 13, 20);

      Long result = mapper.localDateTimeToEpoch(dateTime);

      assertNotNull(result);
      assertEquals(1700000000000L, result);
    }

    @Test
    void shouldReturnNullWhenLocalDateTimeIsNull() {
      Long result = mapper.localDateTimeToEpoch(null);

      assertNull(result);
    }

    @Test
    void shouldConvertCurrentTime() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

      Long result = mapper.localDateTimeToEpoch(now);

      assertNotNull(result);
      assertTrue(result > 0);
    }
  }

  @Nested
  class TestRoundTripConversion {

    @Test
    void shouldRoundTripEpochCorrectly() {
      Long originalEpoch = 1700000000000L;

      LocalDateTime dateTime = mapper.epochToLocalDateTime(originalEpoch);
      Long resultEpoch = mapper.localDateTimeToEpoch(dateTime);

      assertEquals(originalEpoch, resultEpoch);
    }

    @Test
    void shouldRoundTripLocalDateTimeCorrectly() {
      LocalDateTime originalDateTime = LocalDateTime.of(2023, 6, 15, 12, 30, 45);

      Long epoch = mapper.localDateTimeToEpoch(originalDateTime);
      LocalDateTime resultDateTime = mapper.epochToLocalDateTime(epoch);

      assertEquals(originalDateTime, resultDateTime);
    }
  }

  @Nested
  class TestToServiceRequest {

    @Test
    void shouldConvertSnoozeAlertRestRequestToServiceRequest() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertEquals(1, result.getAlertId());
      assertEquals("user@test.com", result.getUpdatedBy());
      assertNotNull(result.getSnoozeFrom());
      assertNotNull(result.getSnoozeUntil());
    }

    @Test
    void shouldHandleNullSnoozeFrom() {
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(null, 1700000000000L);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertNull(result.getSnoozeFrom());
      assertNotNull(result.getSnoozeUntil());
    }

    @Test
    void shouldHandleNullSnoozeUntil() {
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(1700000000000L, null);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertNotNull(result.getSnoozeFrom());
      assertNull(result.getSnoozeUntil());
    }

    @Test
    void shouldHandleBothNullSnoozeTimes() {
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(null, null);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 1, restRequest);

      assertNotNull(result);
      assertNull(result.getSnoozeFrom());
      assertNull(result.getSnoozeUntil());
    }

    @Test
    void shouldSetCorrectAlertId() {
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(null, null);

      SnoozeAlertRequest result = mapper.toServiceRequest("user@test.com", 42, restRequest);

      assertEquals(42, result.getAlertId());
    }

    @Test
    void shouldSetCorrectUserEmail() {
      SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(null, null);

      SnoozeAlertRequest result = mapper.toServiceRequest("different.user@test.com", 1, restRequest);

      assertEquals("different.user@test.com", result.getUpdatedBy());
    }
  }

  @Nested
  class TestNullInputsToAbstractMethods {

    @Test
    void shouldHandleNullServiceResponseForSnoozeAlertRestResponse() {
      var result = mapper.toSnoozeAlertRestResponse(null);

      assertNull(result);
    }

    @Test
    void shouldHandleNullAlertForAlertDetailsResponseDto() {
      var result = mapper.toAlertDetailsResponseDto(null);

      assertNull(result);
    }

    @Test
    void shouldHandleNullGetAlertsResponseForPaginatedDto() {
      var result = mapper.toAlertDetailsPaginatedResponseDto(null);

      assertNull(result);
    }

    @Test
    void shouldHandleNullGetAllAlertsResponseForDto() {
      var result = mapper.toAllAlertDetailsResponseDto(null);

      assertNull(result);
    }
  }
}
