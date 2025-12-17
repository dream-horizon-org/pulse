package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertSeverityResponseDto {
  @NotNull
  @JsonProperty("severity_id")
  Integer severityId;

  @NotNull
  @JsonProperty("name")
  Integer name;

  @NotNull
  @JsonProperty("description")
  String description;

}
