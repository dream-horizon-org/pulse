package org.dreamhorizon.pulseserver.service.alert.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertResponse;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

  @Mock
  AlertsDao alertsDao;

  @Mock
  AlertCronService alertCronService;

  @Mock
  ApplicationConfig applicationConfig;

  AlertService alertService;

  @BeforeEach
  void setup() {
    alertService = new AlertService(alertsDao, alertCronService, applicationConfig);
  }

  @Nested
  class TestGetAlertDetails {

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeTrue() {
      // Arrange: create an Alert with snoozedUntil in the future
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);
      Alert baseAlert = Alert.builder()
          .alertId(1)
          .name("Test Alert")
          .description("desc")
          .evaluationPeriod(1)
          .severityId(1)
          .notificationChannelId(1)
          .notificationWebhookUrl("url")
          .createdBy("user")
          .updatedBy("user")
          .createdAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .updatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .isActive(true)
          .evaluationInterval(1)
          .snoozedFrom(snoozeFrom)
          .snoozedUntil(snoozeUntil)
          .build();
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      // Act
      Alert result = alertService.getAlertDetails(1).blockingGet();

      // Assert
      assertEquals(1, result.getAlertId());
      assertEquals(snoozeUntil, result.getSnoozedUntil());
      // isSnoozed should be true because snoozedUntil is in the future
      assertEquals(Boolean.TRUE, result.getIsSnoozed());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeFalse() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2);
      Alert baseAlert = Alert.builder()
          .alertId(1)
          .name("Test Alert")
          .description("desc")
          .evaluationPeriod(1)
          .severityId(1)
          .notificationChannelId(1)
          .notificationWebhookUrl("url")
          .createdBy("user")
          .updatedBy("user")
          .createdAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .updatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .isActive(true)
          .evaluationInterval(1)
          .snoozedFrom(snoozeFrom)
          .snoozedUntil(snoozeUntil)
          .build();
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      // Act
      Alert result = alertService.getAlertDetails(1).blockingGet();

      // Assert
      assertEquals(1, result.getAlertId());
      assertEquals(snoozeUntil, result.getSnoozedUntil());
      // isSnoozed should be true because snoozedUntil is in the future
      assertEquals(Boolean.FALSE, result.getIsSnoozed());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeFalse_SnoozeFromIsFutureTime() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20);
      Alert baseAlert = Alert.builder()
          .alertId(1)
          .name("Test Alert")
          .description("desc")
          .evaluationPeriod(1)
          .severityId(1)
          .notificationChannelId(1)
          .notificationWebhookUrl("url")
          .createdBy("user")
          .updatedBy("user")
          .createdAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .updatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
          .isActive(true)
          .evaluationInterval(1)
          .snoozedFrom(snoozeFrom)
          .snoozedUntil(snoozeUntil)
          .build();
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      // Act
      Alert result = alertService.getAlertDetails(1).blockingGet();

      // Assert
      assertEquals(1, result.getAlertId());
      assertEquals(snoozeUntil, result.getSnoozedUntil());
      // isSnoozed should be true because snoozedUntil is in the future
      assertEquals(Boolean.FALSE, result.getIsSnoozed());
      verifyNoMoreInteractions(alertsDao);
    }
  }


  @Nested
  class TestSnoozeAlert {

    @Test
    void shouldSnoozeAlert() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      ArgumentCaptor<SnoozeAlertRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SnoozeAlertRequest.class);
      when(alertsDao.snoozeAlert(requestArgumentCaptor.capture())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      SnoozeAlertResponse resp = alertService.snoozeAlert(request).blockingGet();

      assertEquals(true, resp.getIsSnoozed());
      assertEquals(snoozeFrom, resp.getSnoozedFrom());
      assertEquals(snoozeUntil, resp.getSnoozedUntil());
      assertEquals(requestArgumentCaptor.getValue().getAlertId(), request.getAlertId());
      assertEquals(requestArgumentCaptor.getValue().getUpdatedBy(), request.getUpdatedBy());
      assertEquals(requestArgumentCaptor.getValue().getSnoozeUntil(), request.getSnoozeUntil());
      assertEquals(requestArgumentCaptor.getValue().getSnoozeFrom(), request.getSnoozeFrom());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldSnoozeAlertForFutureStartDate() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      ArgumentCaptor<SnoozeAlertRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SnoozeAlertRequest.class);
      when(alertsDao.snoozeAlert(requestArgumentCaptor.capture())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      SnoozeAlertResponse resp = alertService.snoozeAlert(request).blockingGet();

      assertEquals(false, resp.getIsSnoozed());
      assertEquals(snoozeFrom, resp.getSnoozedFrom());
      assertEquals(snoozeUntil, resp.getSnoozedUntil());
      assertEquals(requestArgumentCaptor.getValue().getAlertId(), request.getAlertId());
      assertEquals(requestArgumentCaptor.getValue().getUpdatedBy(), request.getUpdatedBy());
      assertEquals(requestArgumentCaptor.getValue().getSnoozeUntil(), request.getSnoozeUntil());
      assertEquals(requestArgumentCaptor.getValue().getSnoozeFrom(), request.getSnoozeFrom());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfSnoozeFromIsFromMoreThan1Year() {
      // Snooze for more than 1 year (threshold is 365 days)
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusMonths(13))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusMonths(17))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze start duration cannot be more than a year later than now", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfSnoozeFromIsOfPast() {
      // Snooze until a time in the past
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze start duration cannot be of past time", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfSnoozeDurationIsOfMoreThan1Year() {
      // Snooze until a time in the past
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1).plusYears(1).plusMinutes(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Cannot snooze for more than 365 days", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfSnoozeDurationIsLessThan0() {
      // Snooze until a time in the past
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1).minusMinutes(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze duration must be greater than 0", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfAlertDaoThrowsError() {
      SnoozeAlertRequest request = SnoozeAlertRequest
          .builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.snoozeAlert(any())).thenReturn(Single.error(new RuntimeException("DB Error")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("DB Error", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }

  }

  @Nested
  class TestDeleteSnooze {

    @Test
    void shouldDeleteSnooze() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest
          .builder()
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      ArgumentCaptor<DeleteSnoozeRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DeleteSnoozeRequest.class);
      when(alertsDao.deleteSnooze(requestArgumentCaptor.capture())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      EmptyResponse resp = alertService.deleteSnooze(request).blockingGet();

      assertEquals(requestArgumentCaptor.getValue().getAlertId(), request.getAlertId());
      assertEquals(requestArgumentCaptor.getValue().getUpdatedBy(), request.getUpdatedBy());
      verifyNoMoreInteractions(alertsDao);
    }

    @Test
    void shouldThrowErrorIfAlertDaoThrowsError() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest
          .builder()
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.deleteSnooze(any())).thenThrow(new RuntimeException("DB Error"));

      RuntimeException ex = assertThrows(RuntimeException.class, () -> alertService.deleteSnooze(request).blockingGet());

      assertEquals("DB Error", ex.getMessage());
      verifyNoMoreInteractions(alertsDao);
    }
  }
}