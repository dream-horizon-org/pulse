package org.dreamhorizon.pulseserver.service.incident;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.incidentdao.IncidentDao;
import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationEventName;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class IncidentService {

  private static final String DEFAULT_PROJECT_ID = "default-project";

  private final IncidentDao incidentDao;
  private final NotificationService notificationService;
  private final NotificationConfig notificationConfig;

  // ===================== Create =====================

  public Single<CreateIncidentResponseDto> createIncident(CreateIncidentRequestDto request) {
    String projectId = ProjectContext.requireProjectId();

    IncidentRow row = IncidentRow.builder()
        .title(request.getTitle())
        .description(request.getDescription())
        .severity(request.getSeverity())
        .reporterName(request.getReporterName())
        .reporterEmail(request.getReporterEmail())
        .orgIdentifier(projectId)
        .status(IncidentStatus.OPEN)
        .build();

    return incidentDao.insertIncident(row)
        .flatMap(saved -> {
          Map<String, Object> params = buildIncidentParams(saved);
          String slackChannel = notificationConfig.getIncidentConfig().getDefaultSlackChannelId();

          SendNotificationRequestDto slackRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.SLACK))
              .eventName(NotificationEventName.CREATE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .slackChannelIds(List.of(slackChannel))
                  .build())
              .params(params)
              .build();

          SendNotificationRequestDto emailRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.EMAIL))
              .eventName(NotificationEventName.CREATE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .emails(List.of(saved.getReporterEmail()))
                  .build())
              .params(params)
              .build();

          return notificationService.sendNotification(DEFAULT_PROJECT_ID, slackRequest)
              .flatMap(slackResponse ->
                  notificationService.sendNotificationAsync(DEFAULT_PROJECT_ID, emailRequest))
              .map(emailResponse -> toResponseDto(saved));
        })
        .doOnSuccess(res -> log.info("Incident created successfully: id={}", res.getId()))
        .doOnError(error -> log.error("Failed to create incident: title={}", request.getTitle(), error));
  }

  // ===================== Acknowledge =====================

  public Completable acknowledgeIncident(long incidentId, String actionBy) {
    log.info("Acknowledging incident id={}", incidentId);

    return incidentDao.getIncidentById(incidentId)
        .flatMapCompletable(incident -> {
          if (incident.getStatus() != IncidentStatus.OPEN) {
            return Completable.error(new RuntimeException(
                "Cannot acknowledge incident " + incidentId + ": not in OPEN state"));
          }
          Map<String, Object> params = buildIncidentParams(incident);
          params.put("actionBy", actionBy);
          String slackChannel = notificationConfig.getIncidentConfig().getDefaultSlackChannelId();

          SendNotificationRequestDto slackRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.SLACK))
              .eventName(NotificationEventName.ACKNOWLEDGE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .slackChannelIds(List.of(slackChannel))
                  .build())
              .params(params)
              .build();

          SendNotificationRequestDto emailRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.EMAIL))
              .eventName(NotificationEventName.ACKNOWLEDGE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .emails(List.of(incident.getReporterEmail()))
                  .build())
              .params(params)
              .build();

          return notificationService.sendNotification(DEFAULT_PROJECT_ID, slackRequest)
              .flatMap(slackRes ->
                  notificationService.sendNotificationAsync(DEFAULT_PROJECT_ID, emailRequest))
              .flatMap(emailRes -> incidentDao.acknowledgeIncident(incidentId))
              .ignoreElement();
        })
        .doOnComplete(() -> log.info("Incident {} acknowledged successfully", incidentId))
        .doOnError(error -> log.error("Error acknowledging incident {}", incidentId, error));
  }

  // ===================== Recover =====================

  public Completable recoverIncident(long incidentId, String actionBy) {
    log.info("Recovering incident id={}", incidentId);

    return incidentDao.getIncidentById(incidentId)
        .flatMapCompletable(incident -> {
          if (incident.getStatus() != IncidentStatus.ACKNOWLEDGED) {
            return Completable.error(new RuntimeException(
                "Cannot recover incident " + incidentId + ": not in ACKNOWLEDGED state"));
          }
          Map<String, Object> params = buildIncidentParams(incident);
          params.put("actionBy", actionBy);
          String slackChannel = notificationConfig.getIncidentConfig().getDefaultSlackChannelId();

          SendNotificationRequestDto slackRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.SLACK))
              .eventName(NotificationEventName.RECOVERED_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .slackChannelIds(List.of(slackChannel))
                  .build())
              .params(params)
              .build();

          SendNotificationRequestDto emailRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.EMAIL))
              .eventName(NotificationEventName.RECOVERED_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .emails(List.of(incident.getReporterEmail()))
                  .build())
              .params(params)
              .build();

          return notificationService.sendNotification(DEFAULT_PROJECT_ID, slackRequest)
              .flatMap(slackRes ->
                  notificationService.sendNotificationAsync(DEFAULT_PROJECT_ID, emailRequest))
              .flatMap(emailRes -> incidentDao.recoverIncident(incidentId))
              .ignoreElement();
        })
        .doOnComplete(() -> log.info("Incident {} recovered successfully", incidentId))
        .doOnError(error -> log.error("Error recovering incident {}", incidentId, error));
  }

  // ===================== Close =====================

  public Completable closeIncident(long incidentId, String actionBy) {
    log.info("Closing incident id={}", incidentId);

    return incidentDao.getIncidentById(incidentId)
        .flatMapCompletable(incident -> {
          if (incident.getStatus() != IncidentStatus.RECOVERED) {
            return Completable.error(new RuntimeException(
                "Cannot close incident " + incidentId + ": not in RECOVERED state"));
          }
          Map<String, Object> params = buildIncidentParams(incident);
          params.put("actionBy", actionBy);
          String slackChannel = notificationConfig.getIncidentConfig().getDefaultSlackChannelId();

          SendNotificationRequestDto slackRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.SLACK))
              .eventName(NotificationEventName.CLOSE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .slackChannelIds(List.of(slackChannel))
                  .build())
              .params(params)
              .build();

          SendNotificationRequestDto emailRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.EMAIL))
              .eventName(NotificationEventName.CLOSE_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .emails(List.of(incident.getReporterEmail()))
                  .build())
              .params(params)
              .build();

          return notificationService.sendNotification(DEFAULT_PROJECT_ID, slackRequest)
              .flatMap(slackRes ->
                  notificationService.sendNotificationAsync(DEFAULT_PROJECT_ID, emailRequest))
              .flatMap(emailRes -> incidentDao.closeIncident(incidentId))
              .ignoreElement();
        })
        .doOnComplete(() -> log.info("Incident {} closed successfully", incidentId))
        .doOnError(error -> log.error("Error closing incident {}", incidentId, error));
  }

  // ===================== Helpers =====================

  private Map<String, Object> buildIncidentParams(IncidentRow incident) {
    Map<String, Object> params = new HashMap<>();
    params.put("incidentId", String.valueOf(incident.getId()));
    params.put("title", incident.getTitle());
    params.put("description", incident.getDescription());
    params.put("severity", incident.getSeverity().name());
    params.put("orgIdentifier", incident.getOrgIdentifier());
    params.put("reporterName", incident.getReporterName());
    params.put("reporterEmail", incident.getReporterEmail());
    params.put("status", incident.getStatus().name());
    return params;
  }

  private CreateIncidentResponseDto toResponseDto(IncidentRow row) {
    return CreateIncidentResponseDto.builder()
        .id(row.getId())
        .status(row.getStatus())
        .createdAt(row.getCreatedAt())
        .build();
  }
}
