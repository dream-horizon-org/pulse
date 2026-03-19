package org.dreamhorizon.pulseserver.resources.incident;

import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.IncidentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class RestIncidentMapper {

  public static final RestIncidentMapper INSTANCE = Mappers.getMapper(RestIncidentMapper.class);

  public abstract IncidentResponseDto toIncidentResponseDto(IncidentRow row);

  public abstract CreateIncidentResponseDto toCreateIncidentResponseDto(IncidentRow row);

  @Mapping(target = "orgIdentifier", source = "projectId")
  @Mapping(target = "status", constant = "OPEN")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "acknowledgedAt", ignore = true)
  @Mapping(target = "recoveredAt", ignore = true)
  @Mapping(target = "closedAt", ignore = true)
  public abstract IncidentRow toIncidentRow(CreateIncidentRequestDto request, String projectId);
}
