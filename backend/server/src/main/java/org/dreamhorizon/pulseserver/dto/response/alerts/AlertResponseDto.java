package org.dreamhorizon.pulseserver.dto.response.alerts;

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
public class AlertResponseDto {
  @NotNull
  @JsonProperty("alert_id")
  Integer alertId;
}
