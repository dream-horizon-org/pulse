package org.dreamhorizon.pulseserver.service.incident;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.incidentdao.IncidentDao;
import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class IncidentService {

  private final IncidentDao incidentDao;
  private final NotificationService notificationService;

  public Single<CreateIncidentResponseDto> createIncident(CreateIncidentRequestDto request) {
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
          String emailSubject = String.format("[%s] New Incident Reported: %s",
              saved.getSeverity(), saved.getTitle());
          String emailBody = String.format(
              "A new incident has been reported.\n\nTitle: %s\nDescription: %s\nSeverity: %s\nOrg: %s\nReporter: %s <%s>",
              saved.getTitle(), saved.getDescription(), saved.getSeverity(),
              saved.getOrgIdentifier(), saved.getReporterName(), saved.getReporterEmail());
          String slackMessage = String.format(
              "[%s] *New Incident:* %s | Reporter: %s | Org: %s",
              saved.getSeverity(), saved.getTitle(),
              saved.getReporterName(), saved.getOrgIdentifier());

          return notificationService.sendSlackMessage("#incidents", slackMessage)
              .andThen(notificationService.sendEmail(saved.getReporterEmail(), emailSubject, emailBody))
              .andThen(Single.just(toResponseDto(saved)));
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
