package org.dreamhorizon.pulseserver.resources.alert;

import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertDetailsPaginatedResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertDetailsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AllAlertDetailsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAllAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class AlertMapper {

  public static AlertMapper INSTANCE = Mappers.getMapper(AlertMapper.class);

  public abstract UpdateAlertRequest toUpdateAlertRequest(UpdateAlertRequestDto dto);

  public abstract CreateAlertRequest toCreateAlertRequest(CreateAlertRequestDto dto);

  public abstract AlertDetailsResponseDto toAlertDetailsResponseDto(Alert alert);

  public abstract AlertDetailsPaginatedResponseDto toAlertDetailsPaginatedResponseDto(GetAlertsResponse getAlertsResponse);

  public abstract AllAlertDetailsResponseDto toAllAlertDetailsResponseDto(GetAllAlertsResponse getAllAlertsResponse);

  public abstract SnoozeAlertRestResponse toSnoozeAlertRestResponse(SnoozeAlertResponse serviceResponse);

  public SnoozeAlertRequest toServiceRequest(String userEmail, Integer alertId, SnoozeAlertRestRequest snoozeAlertRestRequest) {
    LocalDateTime snoozeFrom = epochToLocalDateTime(snoozeAlertRestRequest.getSnoozeFrom());
    LocalDateTime snoozeUntil = epochToLocalDateTime(snoozeAlertRestRequest.getSnoozeUntil());

    return SnoozeAlertRequest
        .builder()
        .alertId(alertId)
        .snoozeFrom(snoozeFrom)
        .snoozeUntil(snoozeUntil)
        .updatedBy(userEmail)
        .build();
  }

  public LocalDateTime epochToLocalDateTime(Long epoch) {
    return epoch == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
  }

  public Long localDateTimeToEpoch(LocalDateTime localDateTime) {
    return localDateTime == null ? null : localDateTime.toEpochSecond(ZoneOffset.UTC);
  }
}
