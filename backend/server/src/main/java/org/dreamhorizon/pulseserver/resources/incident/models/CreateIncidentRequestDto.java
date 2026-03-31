package org.dreamhorizon.pulseserver.resources.incident.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIncidentRequestDto {

  @NotBlank
  private String title;

  @NotBlank
  private String description;

  @NotNull
  private IncidentSeverity severity = IncidentSeverity.P4;

  @NotBlank
  private String reporterName;

  @NotBlank
  private String reporterEmail;

  @NotBlank
  private String orgIdentifier;
}
