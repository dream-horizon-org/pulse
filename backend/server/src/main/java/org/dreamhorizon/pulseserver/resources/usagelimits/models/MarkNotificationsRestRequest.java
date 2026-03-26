package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkNotificationsRestRequest {
  
  @NotNull(message = "Thresholds cannot be null")
  @NotEmpty(message = "At least one threshold must be provided")
  @JsonProperty("thresholds")
  private List<Integer> thresholds;
}
