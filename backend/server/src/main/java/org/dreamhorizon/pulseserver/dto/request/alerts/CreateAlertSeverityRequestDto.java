package org.dreamhorizon.pulseserver.dto.request.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertSeverityRequestDto {
  @NotNull
  @JsonProperty("name")
  Integer name;

  @NotNull
  @JsonProperty("description")
  String description;
}
