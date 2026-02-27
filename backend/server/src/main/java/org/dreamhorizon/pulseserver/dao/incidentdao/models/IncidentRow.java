package org.dreamhorizon.pulseserver.dao.incidentdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentRow {

  private Long id;
  private String title;
  private String description;
  private IncidentSeverity severity;
  private String reporterName;
  private String reporterEmail;
  private String orgIdentifier;
  private IncidentStatus status;
  private String createdAt;
  private String updatedAt;
}
