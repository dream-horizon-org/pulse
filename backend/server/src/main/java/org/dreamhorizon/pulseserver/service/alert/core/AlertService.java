package org.dreamhorizon.pulseserver.service.alert.core;

import static org.dreamhorizon.pulseserver.constant.Constants.ALERT_EVALUATE_AND_TRIGGER_ALERT;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.dao.AlertsDaoV4;
import org.dreamhorizon.pulseserver.resources.alert.models.ScopeEvaluationHistoryDto;
import org.dreamhorizon.pulseserver.dto.v2.response.EmptyResponse;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.alert.models.AddAlertToCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertEvaluationHistoryResponseDto;
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
import org.dreamhorizon.pulseserver.resources.alert.models.DeleteAlertFromCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.GetAlertsListRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertInCronManager;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAllAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertService {

  private final AlertsDao alertsDao;
  private final AlertsDaoV4 alertsDaoV4;
  private final AlertCronService alertCronService;
  private final ApplicationConfig applicationConfig;

  private static void validateSnoozeFrom(LocalDateTime start, int snoozeSecondsThreshold) {
    if (start.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))) {
      throw ServiceError
          .INVALID_REQUEST_PARAM
          .getCustomException("Snooze start duration cannot be of past time", "Snooze start duration cannot be of past time", 400);
    }

    if (start.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(snoozeSecondsThreshold))) {
      throw ServiceError
          .INVALID_REQUEST_PARAM
          .getCustomException("Snooze start duration cannot be more than a year later than now",
              "Snooze start duration cannot be more than a year later than now", 400);
    }
  }

  private static void validateSnoozeDuration(long snoozedForSeconds, int snoozeDaysThreshold) {
    if (snoozedForSeconds > snoozeDaysThreshold) {
      throw ServiceError
          .INVALID_REQUEST_PARAM
          .getCustomException("Cannot snooze for more than 365 days", "Cannot snooze for more than 365 days", 400);
    }

    if (snoozedForSeconds <= 0) {
      throw ServiceError
          .INVALID_REQUEST_PARAM
          .getCustomException("Snooze duration must be greater than 0", "Snooze duration must be greater than 0", 400);
    }
  }

  public Single<AlertResponseDto> createAlert(@Valid @NotNull CreateAlertRequest createAlertRequestDto) {
    return alertsDao
        .createAlert(createAlertRequestDto)
        .flatMap(alertId -> createAlertCron(createAlertRequestDto, alertId));
  }

  @NotNull
  private Single<AlertResponseDto> createAlertCron(@NotNull CreateAlertRequest createAlertRequestDto, Integer alertId) {
    return alertCronService.createAlertCron(new AddAlertToCronManager(
        alertId,
        createAlertRequestDto.getEvaluationInterval(),
        applicationConfig.getServiceUrl() + ALERT_EVALUATE_AND_TRIGGER_ALERT + "?alertId=" + alertId
    )).map(created -> {
      if (!created) {
        log.error("Error while adding alert to cron manager for alertId: {}", alertId);
        throw ServiceError.CRON_SERVICE_ERROR.getCustomException("Error while adding alert to cron manager for alert id: {}", alertId);
      }

      return new AlertResponseDto(alertId);
    }).doOnError(error -> {
      log.error("Error while adding alert to cron manager for alertId: {}", alertId, error);
      throw ServiceError.CRON_SERVICE_ERROR.getCustomException(
          "Alert created but error while updating alert in cron manager for alert id: {}. Please try updating your alert again", alertId);
    });
  }

  public Single<AlertResponseDto> updateAlert(@Valid @NotNull UpdateAlertRequest updateAlertRequestDto) {
    Integer alertId = updateAlertRequestDto.getAlertId();
    AtomicReference<Integer> evaluationInterval = new AtomicReference<>();

    return alertsDao.getAlertDetails(alertId)
        .flatMap(alertDetailsResponseDto -> {
          evaluationInterval.set(alertDetailsResponseDto.getEvaluationInterval());
          return alertsDao.updateAlert(updateAlertRequestDto);
        })
        .flatMap(updatedAlertId -> alertCronService.updateAlertCron(new UpdateAlertInCronManager(
            alertId,
            updateAlertRequestDto.getEvaluationInterval(),
            evaluationInterval.get(),
            applicationConfig.getServiceUrl() + ALERT_EVALUATE_AND_TRIGGER_ALERT + "?alertId=" + updatedAlertId
        )).map(updated -> {
          if (!updated) {
            log.error("Error while updating alert in cron manager for alertId: {}", updatedAlertId);
            throw ServiceError.CRON_SERVICE_ERROR.getCustomException("Error while updating alert in cron manager for alert id: {}",
                updatedAlertId);
          }

          return new AlertResponseDto(updatedAlertId);
        }).doOnError(error -> {
          log.error("Error while updating alert in cron manager for alertId: {}", updatedAlertId, error);
          throw ServiceError.CRON_SERVICE_ERROR.getCustomException(
              "Alert updated but error while updating alert in cron manager for alert id: {}", updatedAlertId);
        }));

  }

  public Single<SnoozeAlertResponse> snoozeAlert(@Valid SnoozeAlertRequest request) {
    LocalDateTime start = request.getSnoozeFrom();
    LocalDateTime end = request.getSnoozeUntil();

    int snoozeSecondsThreshold = 60 * 60 * 24 * 365; // 1 year threshold. should we move this to config?
    long snoozedForSeconds = ChronoUnit.SECONDS.between(start, end);

    validateSnoozeFrom(start, snoozeSecondsThreshold);
    validateSnoozeDuration(snoozedForSeconds, snoozeSecondsThreshold);

    return alertsDao
        .snoozeAlert(request)
        .map(resp -> SnoozeAlertResponse
            .builder()
            .isSnoozed(isAlertSnoozed(start, end))
            .snoozedFrom(start)
            .snoozedUntil(end)
            .build());
  }

  public Single<EmptyResponse> deleteSnooze(@Valid DeleteSnoozeRequest request) {
    return alertsDao.deleteSnooze(request);
  }

  public Single<Boolean> deleteAlert(@NotNull Integer alertId) {
    return getAlertDetails(alertId)
        .flatMap(alert -> alertsDao.deleteAlert(alertId)
            .flatMap(deleted -> {
              if (deleted) {
                // TODO : Remove alert from cron job
                return alertCronService.deleteAlertCron(new DeleteAlertFromCronManager(alert.getAlertId(), alert.getEvaluationInterval()))
                    .map(deletedCron -> {
                      if (!deletedCron) {
                        log.error("Error while deleting alert from cron manager for alertId: {}", alertId);
                        throw ServiceError.CRON_SERVICE_ERROR.getCustomException(
                            "Error while deleting alert from cron manager for alert id: {}", alertId);
                      }

                      return true;
                    }).doOnError(error -> {
                      log.error("Error while deleting alert from cron manager for alertId: {}", alertId, error);
                    });
              }

              log.error("Error while deleting alert for alertId: {}", alertId);
              return Single.error(ServiceError.DATABASE_ERROR.getCustomException("Error while deleting alert"));
            })).doOnError(e -> log.error("Error while deleting alert for alertId: {}", alertId, e));
  }

  public Single<Alert> getAlertDetails(@NotNull Integer alertId) {
    return alertsDao
        .getAlertDetails(alertId)
        .flatMap(alert -> Single.just(populateSnoozeStatus(alert)));
  }

  public Single<GetAlertsResponse> getAlerts(GetAlertsListRequestDto getAlertsListRequestDto) {
    return alertsDao.getAlerts(
        getAlertsListRequestDto.getName(),
        getAlertsListRequestDto.getScope(),
        getAlertsListRequestDto.getLimit(),
        getAlertsListRequestDto.getOffset(),
        getAlertsListRequestDto.getCreatedBy(),
        getAlertsListRequestDto.getUpdatedBy()
    ).flatMap(alertsResponse -> {
      List<Alert> updatedAlerts = alertsResponse
          .getAlerts()
          .stream()
          .map(this::populateSnoozeStatus)
          .collect(Collectors.toList());

      alertsResponse.setAlerts(updatedAlerts);
      return Single.just(alertsResponse);
    });
  }

  public Single<GetAllAlertsResponse> getAllAlerts() {
    return alertsDao.getAllAlerts()
        .map(response -> {
          List<Alert> updatedAlerts = response.getAlerts()
              .stream()
              .map(this::populateSnoozeStatus)
              .collect(Collectors.toList());
          response.setAlerts(updatedAlerts);
          return response;
        });
  }

  private Alert populateSnoozeStatus(Alert alert) {
    return alert
        .toBuilder()
        .isSnoozed(isAlertSnoozed(alert))
        .build();
  }

  public Single<List<AlertEvaluationHistoryResponseDto>> getAlertEvaluationHistory(@NotNull Integer alertId) {
    return alertsDao.getEvaluationHistoryOfAlert(alertId);
  }

  public Single<List<ScopeEvaluationHistoryDto>> getAlertEvaluationHistoryByScope(@NotNull Integer alertId) {
    return alertsDaoV4.getEvaluationHistoryByAlert(alertId);
  }

  public Single<List<AlertSeverityResponseDto>> getAlertSeverities() {
    return alertsDao.getAlertSeverities();
  }

  public Single<Boolean> createAlertSeverity(@NotNull CreateAlertSeverityRequestDto severity) {
    return alertsDao.createAlertSeverity(severity.getName(), severity.getDescription());
  }

  public Single<List<AlertNotificationChannelResponseDto>> getAlertNotificationChannels() {
    return alertsDao.getNotificationChannels();
  }

  public Single<Boolean> createAlertNotificationChannel(@NotNull CreateAlertNotificationChannelRequestDto notificationChannel) {
    return alertsDao.createNotificationChannel(notificationChannel.getName(), notificationChannel.getConfig());
  }

  public Single<Boolean> createTag(@NotNull String tag) {
    return alertsDao.createTagForAlert(tag);
  }

  public Single<Boolean> createTagAndAlertMapping(@NotNull Integer alertId, @NotNull AlertTagMapRequestDto alertTagMapRequestDto) {
    return alertsDao.createTagAndAlertMapping(alertId, alertTagMapRequestDto.getTagId());
  }

  public Single<List<AlertTagsResponseDto>> getTags() {
    return alertsDao.getAllTags();
  }

  public Single<List<AlertTagsResponseDto>> getTagsForAlert(@NotNull Integer alertId) {
    return alertsDao.getTagsByAlertId(alertId);
  }

  public Single<Boolean> deleteAlertTagMapping(@NotNull Integer alertId, @NotNull AlertTagMapRequestDto alertTagMapRequestDto) {
    return alertsDao.deleteAlertTagMapping(alertId, alertTagMapRequestDto.getTagId());
  }

  public boolean isAlertSnoozed(Alert alert) {
    return isAlertSnoozed(alert.getSnoozedFrom(), alert.getSnoozedUntil());
  }

  public boolean isAlertSnoozed(LocalDateTime snoozedFrom, LocalDateTime snoozedUntil) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    if (Objects.isNull(snoozedFrom) || Objects.isNull(snoozedUntil)) {
      return false;
    }

    return (snoozedFrom.isEqual(now) || snoozedFrom.isBefore(now))
        && snoozedUntil.isAfter(now);
  }

  public Single<AlertFiltersResponseDto> getAlertFilters() {
    return alertsDao.getAlertsFilters();
  }

  public Single<AlertScopesResponseDto> getAlertScopes() {
    List<AlertScopeItemDto> scopes = Arrays.stream(AlertScope.values())
        .map(this::mapScopeToDto)
        .collect(Collectors.toList());
    AlertScopesResponseDto response = AlertScopesResponseDto.builder()
        .scopes(scopes)
        .build();
    return Single.just(response);
  }

  private AlertScopeItemDto mapScopeToDto(AlertScope scope) {
    String id = switch (scope) {
      case Interaction -> "interaction";
      case network -> "network_api";
      case SCREEN -> "screen";
      case APP_VITALS -> "app_vitals";
    };
    String label = switch (scope) {
      case Interaction -> "Interactions";
      case network -> "Network APIs";
      case SCREEN -> "Screen";
      case APP_VITALS -> "App Vitals";
    };
    return AlertScopeItemDto.builder()
        .id(id)
        .label(label)
        .build();
  }

  public Single<AlertMetricsResponseDto> getAlertMetrics(String scope) {
    AlertScope alertScope = parseScope(scope);
    List<String> metrics = getMetricsForScope(alertScope);
    AlertMetricsResponseDto response = AlertMetricsResponseDto.builder()
        .scope(scope)
        .metrics(metrics)
        .build();
    return Single.just(response);
  }

  private AlertScope parseScope(String scope) {
    return switch (scope.toLowerCase()) {
      case "interaction" -> AlertScope.Interaction;
      case "network_api" -> AlertScope.network;
      case "screen" -> AlertScope.SCREEN;
      case "app_vitals" -> AlertScope.APP_VITALS;
      default -> throw ServiceError.INVALID_REQUEST_PARAM.getCustomException(
          "Invalid scope: {}. Valid scopes are: interaction, network_api, screen, app_vitals", scope);
    };
  }

  private List<String> getMetricsForScope(AlertScope scope) {
    return switch (scope) {
      case Interaction -> List.of(
          "INTERACTION_SUCCESS_COUNT",
          "INTERACTION_ERROR_COUNT",
          "INTERACTION_ERROR_DISTINCT_USERS",
          "INTERACTION_CATEGORY_POOR",
          "INTERACTION_CATEGORY_AVERAGE",
          "INTERACTION_CATEGORY_GOOD",
          "INTERACTION_CATEGORY_EXCELLENT",
          "INTERACTION_TIME_P99",
          "INTERACTION_TIME_P95",
          "INTERACTION_TIME_P50",
          "APDEX_SCORE",
          "INTERACTION_TIME",
          "INTERACTION_CATEGORY",
          "ERROR_RATE",
          "USER_CATEGORY_EXCELLENT",
          "USER_CATEGORY_GOOD",
          "USER_CATEGORY_AVERAGE",
          "USER_CATEGORY_POOR"
      );
      case SCREEN -> List.of(
          "SCREEN_LOAD_TIME_P99",
          "SCREEN_LOAD_TIME_P95",
          "SCREEN_LOAD_TIME_P50",
          "SCREEN_DAILY_USERS",
          "SCREEN_ACTIVE_USERS",
          "SCREEN_ERROR_RATE",
          "SCREEN_TIME"
      );
      case APP_VITALS -> List.of(
          "CRASH_RATE",
          "ANR_RATE",
          "FROZEN_FRAME_RATE",
          "POOR_USER_RATE",
          "AVERAGE_USER_RATE",
          "GOOD_USER_RATE",
          "EXCELLENT_USER_RATE",
          "LOAD_TIME"
      );
      case network -> List.of(
          "NET_4XX_RATE",
          "NET_5XX_RATE",
          "NET_0",
          "NET_2XX",
          "NET_3XX",
          "NET_4XX",
          "NET_5XX"
      );
    };
  }
}

