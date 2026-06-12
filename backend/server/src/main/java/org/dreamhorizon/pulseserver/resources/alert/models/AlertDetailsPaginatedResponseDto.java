package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertDetailsPaginatedResponseDto {
  @NotNull
  @JsonProperty("total_alerts")
  Integer totalAlerts;

  @NotNull
  @JsonProperty("alerts")
  List<AlertDetailsResponseDto> alerts;

  @NotNull
  @JsonProperty("offset")
  Integer page;

  @NotNull
  @JsonProperty("limit")
  Integer limit;
}
