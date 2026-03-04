package org.dreamhorizon.pulseserver.service.incident;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
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

  private final IncidentDao incidentDao;
  private final NotificationService notificationService;
  private final NotificationConfig notificationConfig;

  public Single<CreateIncidentResponseDto> createIncident(CreateIncidentRequestDto request) {
    String projectId = ProjectContext.requireProjectId();

    IncidentRow row = IncidentRow.builder()
        .title(request.getTitle())
        .description(request.getDescription())
        .severity(request.getSeverity())
        .reporterName(request.getReporterName())
        .reporterEmail(request.getReporterEmail())
        .orgIdentifier(request.getOrgIdentifier())
        .status(IncidentStatus.OPEN)
        .build();

    return incidentDao.insertIncident(row)
        .flatMap(saved -> {
          String slackChannel = notificationConfig.getIncidentConfig().getDefaultSlackChannelId();
          Map<String, Object> params = Map.of(
              "title", saved.getTitle(),
              "description", saved.getDescription(),
              "severity", saved.getSeverity().name(),
              "orgIdentifier", saved.getOrgIdentifier(),
              "reporterName", saved.getReporterName(),
              "reporterEmail", saved.getReporterEmail()
          );

          SendNotificationRequestDto slackRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.SLACK))
              .eventName(NotificationEventName.NEW_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .slackChannelIds(List.of(slackChannel))
                  .build())
              .params(params)
              .build();

          SendNotificationRequestDto emailRequest = SendNotificationRequestDto.builder()
              .channelTypes(List.of(ChannelType.EMAIL))
              .eventName(NotificationEventName.NEW_INCIDENT.getValue())
              .recipients(RecipientsDto.builder()
                  .emails(List.of(saved.getReporterEmail()))
                  .build())
              .params(params)
              .build();

          return notificationService.sendNotificationAsync(projectId, slackRequest)
              .flatMap(slackResponse ->
                  notificationService.sendNotificationAsync(projectId, emailRequest))
              .map(emailResponse -> toResponseDto(saved));
        })
        .doOnSuccess(res -> log.info("Incident created successfully: id={}", res.getId()))
        .doOnError(error -> log.error("Failed to create incident: title={}", request.getTitle(), error));
  }

  private CreateIncidentResponseDto toResponseDto(IncidentRow row) {
    return CreateIncidentResponseDto.builder()
        .id(row.getId())
        .status(row.getStatus())
        .createdAt(row.getCreatedAt())
        .build();
  }
}
