package org.dreamhorizon.pulseserver.dto.request.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddAlertToCronManager {
  @NotNull
  @JsonProperty("id")
  Integer id;

  @NotNull
  @JsonProperty("interval")
  Integer interval;

  @NotNull
  @JsonProperty("url")
  String url;
}
