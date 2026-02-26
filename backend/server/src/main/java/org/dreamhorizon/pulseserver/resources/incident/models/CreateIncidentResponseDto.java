package org.dreamhorizon.pulseserver.resources.incident.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIncidentResponseDto {

  private Long id;
  private IncidentStatus status;
  private String createdAt;
}
