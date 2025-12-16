package org.dreamhorizon.pulseserver.service.alert.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertMetricsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertScopeItemDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertScopesResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagMapRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertNotificationChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertSeverityRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.GetAlertsListRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.MetricItemDto;
import org.dreamhorizon.pulseserver.resources.alert.models.ScopeEvaluationHistoryDto;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAllAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;
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
    when(applicationConfig.getServiceUrl()).thenReturn("http://localhost:8080");
  }

  @Nested
  class TestGetAlertDetails {

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeTrue() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);
      Alert baseAlert = createBaseAlert(1, snoozeFrom, snoozeUntil);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      Alert result = alertService.getAlertDetails(1).blockingGet();

      assertEquals(1, result.getAlertId());
      assertEquals(snoozeUntil, result.getSnoozedUntil());
      assertEquals(Boolean.TRUE, result.getIsSnoozed());
    }

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeFalse() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2);
      Alert baseAlert = createBaseAlert(1, snoozeFrom, snoozeUntil);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      Alert result = alertService.getAlertDetails(1).blockingGet();

      assertEquals(1, result.getAlertId());
      assertEquals(Boolean.FALSE, result.getIsSnoozed());
    }

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeFalseWhenSnoozeFromIsFuture() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20);
      Alert baseAlert = createBaseAlert(1, snoozeFrom, snoozeUntil);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      Alert result = alertService.getAlertDetails(1).blockingGet();

      assertEquals(Boolean.FALSE, result.getIsSnoozed());
    }

    @Test
    void shouldReturnAlertDetailsAndIsSnoozedShouldBeFalseWhenSnoozeIsNull() {
      Alert baseAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(baseAlert));

      Alert result = alertService.getAlertDetails(1).blockingGet();

      assertEquals(Boolean.FALSE, result.getIsSnoozed());
    }
  }

  @Nested
  class TestCreateAlert {

    @Test
    void shouldCreateAlertSuccessfully() {
      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("Test Alert")
          .description("Test Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("user")
          .updatedBy("user")
          .scope(AlertScope.interaction)
          .conditionExpression("A && B")
          .alerts(new ArrayList<>())
          .build();

      when(alertsDao.createAlert(any())).thenReturn(Single.just(1));
      when(alertCronService.createAlertCron(any())).thenReturn(Single.just(true));

      AlertResponseDto result = alertService.createAlert(request).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getAlert_id());
      verify(alertsDao).createAlert(any());
      verify(alertCronService).createAlertCron(any());
    }

    @Test
    void shouldThrowErrorWhenCronCreationFails() {
      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("Test Alert")
          .description("Test Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("user")
          .updatedBy("user")
          .scope(AlertScope.interaction)
          .conditionExpression("A && B")
          .alerts(new ArrayList<>())
          .build();

      when(alertsDao.createAlert(any())).thenReturn(Single.just(1));
      when(alertCronService.createAlertCron(any())).thenReturn(Single.just(false));

      assertThrows(Exception.class, () -> alertService.createAlert(request).blockingGet());
    }
  }

  @Nested
  class TestUpdateAlert {

    @Test
    void shouldUpdateAlertSuccessfully() {
      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Updated Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("user")
          .updatedBy("user")
          .scope(AlertScope.interaction)
          .conditionExpression("A && B")
          .alerts(new ArrayList<>())
          .build();

      Alert existingAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(existingAlert));
      when(alertsDao.updateAlert(any())).thenReturn(Single.just(1));
      when(alertCronService.updateAlertCron(any())).thenReturn(Single.just(true));

      AlertResponseDto result = alertService.updateAlert(request).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getAlert_id());
    }

    @Test
    void shouldThrowErrorWhenCronUpdateFails() {
      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Updated Description")
          .evaluationPeriod(60)
          .evaluationInterval(300)
          .severity(1)
          .notificationChannelId(1)
          .createdBy("user")
          .updatedBy("user")
          .scope(AlertScope.interaction)
          .conditionExpression("A && B")
          .alerts(new ArrayList<>())
          .build();

      Alert existingAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(existingAlert));
      when(alertsDao.updateAlert(any())).thenReturn(Single.just(1));
      when(alertCronService.updateAlertCron(any())).thenReturn(Single.just(false));

      assertThrows(Exception.class, () -> alertService.updateAlert(request).blockingGet());
    }
  }

  @Nested
  class TestDeleteAlert {

    @Test
    void shouldDeleteAlertSuccessfully() {
      Alert existingAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(existingAlert));
      when(alertsDao.deleteAlert(1)).thenReturn(Single.just(true));
      when(alertCronService.deleteAlertCron(any())).thenReturn(Single.just(true));

      Boolean result = alertService.deleteAlert(1).blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldThrowErrorWhenCronDeleteFails() {
      Alert existingAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(existingAlert));
      when(alertsDao.deleteAlert(1)).thenReturn(Single.just(true));
      when(alertCronService.deleteAlertCron(any())).thenReturn(Single.just(false));

      assertThrows(Exception.class, () -> alertService.deleteAlert(1).blockingGet());
    }

    @Test
    void shouldThrowErrorWhenAlertDeleteFails() {
      Alert existingAlert = createBaseAlert(1, null, null);
      when(alertsDao.getAlertDetails(1)).thenReturn(Single.just(existingAlert));
      when(alertsDao.deleteAlert(1)).thenReturn(Single.just(false));

      assertThrows(Exception.class, () -> alertService.deleteAlert(1).blockingGet());
    }
  }

  @Nested
  class TestSnoozeAlert {

    @Test
    void shouldSnoozeAlert() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.snoozeAlert(any())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      SnoozeAlertResponse resp = alertService.snoozeAlert(request).blockingGet();

      assertEquals(true, resp.getIsSnoozed());
      assertEquals(snoozeFrom, resp.getSnoozedFrom());
      assertEquals(snoozeUntil, resp.getSnoozedUntil());
    }

    @Test
    void shouldSnoozeAlertForFutureStartDate() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.snoozeAlert(any())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      SnoozeAlertResponse resp = alertService.snoozeAlert(request).blockingGet();

      assertEquals(false, resp.getIsSnoozed());
    }

    @Test
    void shouldThrowErrorIfSnoozeFromIsFromMoreThan1Year() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusMonths(13))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusMonths(17))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze start duration cannot be more than a year later than now", ex.getMessage());
    }

    @Test
    void shouldThrowErrorIfSnoozeFromIsOfPast() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze start duration cannot be of past time", ex.getMessage());
    }

    @Test
    void shouldThrowErrorIfSnoozeDurationIsOfMoreThan1Year() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1).plusYears(1).plusMinutes(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Cannot snooze for more than 365 days", ex.getMessage());
    }

    @Test
    void shouldThrowErrorIfSnoozeDurationIsLessThan0() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1).minusMinutes(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();

      Exception ex = assertThrows(Exception.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("Snooze duration must be greater than 0", ex.getMessage());
    }

    @Test
    void shouldThrowErrorIfAlertDaoThrowsError() {
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .snoozeFrom(LocalDateTime.now(ZoneOffset.UTC))
          .snoozeUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.snoozeAlert(any())).thenReturn(Single.error(new RuntimeException("DB Error")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertService.snoozeAlert(request).blockingGet());
      assertEquals("DB Error", ex.getMessage());
    }
  }

  @Nested
  class TestDeleteSnooze {

    @Test
    void shouldDeleteSnooze() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.deleteSnooze(any())).thenReturn(Single.just(EmptyResponse.emptyResponse));

      EmptyResponse resp = alertService.deleteSnooze(request).blockingGet();

      assertNotNull(resp);
    }

    @Test
    void shouldThrowErrorIfAlertDaoThrowsError() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(1)
          .updatedBy("mock_user")
          .build();
      when(alertsDao.deleteSnooze(any())).thenThrow(new RuntimeException("DB Error"));

      RuntimeException ex = assertThrows(RuntimeException.class, () -> alertService.deleteSnooze(request).blockingGet());
      assertEquals("DB Error", ex.getMessage());
    }
  }

  @Nested
  class TestGetAlerts {

    @Test
    void shouldGetAlertsSuccessfully() {
      GetAlertsListRequestDto request = new GetAlertsListRequestDto(
          "name", "scope", "createdBy", "updatedBy", 10, 0
      );

      List<Alert> alerts = List.of(createBaseAlert(1, null, null), createBaseAlert(2, null, null));
      GetAlertsResponse response = GetAlertsResponse.builder()
          .alerts(alerts)
          .totalAlerts(2)
          .page(0)
          .limit(10)
          .build();

      when(alertsDao.getAlerts(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString()))
          .thenReturn(Single.just(response));

      GetAlertsResponse result = alertService.getAlerts(request).blockingGet();

      assertNotNull(result);
      assertEquals(2, result.getTotalAlerts());
      assertEquals(2, result.getAlerts().size());
    }

    @Test
    void shouldPopulateSnoozeStatusForEachAlert() {
      GetAlertsListRequestDto request = new GetAlertsListRequestDto();
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

      List<Alert> alerts = List.of(
          createBaseAlert(1, snoozeFrom, snoozeUntil),
          createBaseAlert(2, null, null)
      );
      GetAlertsResponse response = GetAlertsResponse.builder()
          .alerts(alerts)
          .totalAlerts(2)
          .page(0)
          .limit(10)
          .build();

      when(alertsDao.getAlerts(any(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(response));

      GetAlertsResponse result = alertService.getAlerts(request).blockingGet();

      assertTrue(result.getAlerts().get(0).getIsSnoozed());
      assertFalse(result.getAlerts().get(1).getIsSnoozed());
    }
  }

  @Nested
  class TestGetAllAlerts {

    @Test
    void shouldGetAllAlertsSuccessfully() {
      List<Alert> alerts = List.of(createBaseAlert(1, null, null), createBaseAlert(2, null, null));
      GetAllAlertsResponse response = GetAllAlertsResponse.builder()
          .alerts(alerts)
          .build();

      when(alertsDao.getAllAlerts()).thenReturn(Single.just(response));

      GetAllAlertsResponse result = alertService.getAllAlerts().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.getAlerts().size());
    }

    @Test
    void shouldPopulateSnoozeStatusForAllAlerts() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

      List<Alert> alerts = List.of(
          createBaseAlert(1, snoozeFrom, snoozeUntil),
          createBaseAlert(2, null, null)
      );
      GetAllAlertsResponse response = GetAllAlertsResponse.builder()
          .alerts(alerts)
          .build();

      when(alertsDao.getAllAlerts()).thenReturn(Single.just(response));

      GetAllAlertsResponse result = alertService.getAllAlerts().blockingGet();

      assertTrue(result.getAlerts().get(0).getIsSnoozed());
      assertFalse(result.getAlerts().get(1).getIsSnoozed());
    }
  }

  @Nested
  class TestGetAlertEvaluationHistory {

    @Test
    void shouldGetEvaluationHistoryByScope() {
      List<ScopeEvaluationHistoryDto> history = new ArrayList<>();
      when(alertsDao.getEvaluationHistoryByAlert(1)).thenReturn(Single.just(history));

      List<ScopeEvaluationHistoryDto> result = alertService.getAlertEvaluationHistoryByScope(1).blockingGet();

      assertNotNull(result);
    }
  }

  @Nested
  class TestGetAlertSeverities {

    @Test
    void shouldGetAlertSeverities() {
      List<AlertSeverityResponseDto> severities = new ArrayList<>();
      when(alertsDao.getAlertSeverities()).thenReturn(Single.just(severities));

      List<AlertSeverityResponseDto> result = alertService.getAlertSeverities().blockingGet();

      assertNotNull(result);
    }
  }

  @Nested
  class TestCreateAlertSeverity {

    @Test
    void shouldCreateAlertSeverity() {
      CreateAlertSeverityRequestDto request = new CreateAlertSeverityRequestDto(1, "High severity");
      when(alertsDao.createAlertSeverity(anyInt(), anyString())).thenReturn(Single.just(true));

      Boolean result = alertService.createAlertSeverity(request).blockingGet();

      assertTrue(result);
    }
  }

  @Nested
  class TestGetAlertNotificationChannels {

    @Test
    void shouldGetNotificationChannels() {
      List<AlertNotificationChannelResponseDto> channels = new ArrayList<>();
      when(alertsDao.getNotificationChannels()).thenReturn(Single.just(channels));

      List<AlertNotificationChannelResponseDto> result = alertService.getAlertNotificationChannels().blockingGet();

      assertNotNull(result);
    }
  }

  @Nested
  class TestCreateAlertNotificationChannel {

    @Test
    void shouldCreateNotificationChannel() {
      CreateAlertNotificationChannelRequestDto request = new CreateAlertNotificationChannelRequestDto();
      request.setName("Slack");
      request.setConfig("{}");
      when(alertsDao.createNotificationChannel(anyString(), anyString())).thenReturn(Single.just(true));

      Boolean result = alertService.createAlertNotificationChannel(request).blockingGet();

      assertTrue(result);
    }
  }

  @Nested
  class TestTags {

    @Test
    void shouldCreateTag() {
      when(alertsDao.createTagForAlert(anyString())).thenReturn(Single.just(true));

      Boolean result = alertService.createTag("test-tag").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldCreateTagAndAlertMapping() {
      AlertTagMapRequestDto request = new AlertTagMapRequestDto();
      request.setTagId(1);
      when(alertsDao.createTagAndAlertMapping(anyInt(), anyInt())).thenReturn(Single.just(true));

      Boolean result = alertService.createTagAndAlertMapping(1, request).blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldGetTags() {
      List<AlertTagsResponseDto> tags = new ArrayList<>();
      when(alertsDao.getAllTags()).thenReturn(Single.just(tags));

      List<AlertTagsResponseDto> result = alertService.getTags().blockingGet();

      assertNotNull(result);
    }

    @Test
    void shouldGetTagsForAlert() {
      List<AlertTagsResponseDto> tags = new ArrayList<>();
      when(alertsDao.getTagsByAlertId(1)).thenReturn(Single.just(tags));

      List<AlertTagsResponseDto> result = alertService.getTagsForAlert(1).blockingGet();

      assertNotNull(result);
    }

    @Test
    void shouldDeleteAlertTagMapping() {
      AlertTagMapRequestDto request = new AlertTagMapRequestDto();
      request.setTagId(1);
      when(alertsDao.deleteAlertTagMapping(anyInt(), anyInt())).thenReturn(Single.just(true));

      Boolean result = alertService.deleteAlertTagMapping(1, request).blockingGet();

      assertTrue(result);
    }
  }

  @Nested
  class TestGetAlertFilters {

    @Test
    void shouldGetAlertFilters() {
      AlertFiltersResponseDto filters = new AlertFiltersResponseDto();
      when(alertsDao.getAlertsFilters()).thenReturn(Single.just(filters));

      AlertFiltersResponseDto result = alertService.getAlertFilters().blockingGet();

      assertNotNull(result);
    }
  }

  @Nested
  class TestGetAlertScopes {

    @Test
    void shouldGetAlertScopes() {
      List<AlertScopeItemDto> scopes = new ArrayList<>();
      when(alertsDao.getAlertScopes()).thenReturn(Single.just(scopes));

      AlertScopesResponseDto result = alertService.getAlertScopes().blockingGet();

      assertNotNull(result);
      assertNotNull(result.getScopes());
    }
  }

  @Nested
  class TestGetAlertMetrics {

    @Test
    void shouldGetAlertMetrics() {
      List<MetricItemDto> metrics = new ArrayList<>();
      when(alertsDao.getMetricsByScope(anyString())).thenReturn(Single.just(metrics));

      AlertMetricsResponseDto result = alertService.getAlertMetrics("INTERACTION").blockingGet();

      assertNotNull(result);
      assertEquals("INTERACTION", result.getScope());
      assertNotNull(result.getMetrics());
    }
  }

  @Nested
  class TestIsAlertSnoozed {

    @Test
    void shouldReturnTrueWhenAlertIsSnoozed() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

      boolean result = alertService.isAlertSnoozed(snoozeFrom, snoozeUntil);

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenSnoozeHasExpired() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);

      boolean result = alertService.isAlertSnoozed(snoozeFrom, snoozeUntil);

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSnoozeFromIsNull() {
      boolean result = alertService.isAlertSnoozed(null, LocalDateTime.now());

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSnoozeUntilIsNull() {
      boolean result = alertService.isAlertSnoozed(LocalDateTime.now(), null);

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenBothAreNull() {
      boolean result = alertService.isAlertSnoozed(null, null);

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSnoozeFromIsInFuture() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

      boolean result = alertService.isAlertSnoozed(snoozeFrom, snoozeUntil);

      assertFalse(result);
    }

    @Test
    void shouldReturnTrueWhenSnoozeFromEqualsNow() {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      LocalDateTime snoozeUntil = now.plusHours(1);

      boolean result = alertService.isAlertSnoozed(now, snoozeUntil);

      assertTrue(result);
    }

    @Test
    void shouldCheckIsAlertSnoozedWithAlertObject() {
      LocalDateTime snoozeFrom = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
      LocalDateTime snoozeUntil = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
      Alert alert = createBaseAlert(1, snoozeFrom, snoozeUntil);

      boolean result = alertService.isAlertSnoozed(alert);

      assertTrue(result);
    }
  }

  @Nested
  class TestServiceGettersAndSetters {

    @Test
    void shouldGetAlertsDao() {
      assertEquals(alertsDao, alertService.getAlertsDao());
    }

    @Test
    void shouldGetAlertCronService() {
      assertEquals(alertCronService, alertService.getAlertCronService());
    }

    @Test
    void shouldGetApplicationConfig() {
      assertEquals(applicationConfig, alertService.getApplicationConfig());
    }
  }

  // Helper method to create a base alert
  private Alert createBaseAlert(Integer alertId, LocalDateTime snoozeFrom, LocalDateTime snoozeUntil) {
    return Alert.builder()
        .alertId(alertId)
        .name("Test Alert")
        .description("desc")
        .evaluationPeriod(1)
        .severityId(1)
        .notificationChannelId(1)
        .notificationWebhookUrl("url")
        .createdBy("user")
        .updatedBy("user")
        .createdAt(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
        .updatedAt(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
        .isActive(true)
        .evaluationInterval(1)
        .snoozedFrom(snoozeFrom)
        .snoozedUntil(snoozeUntil)
        .build();
  }
}
