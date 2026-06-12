package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAlertFromCronManager {
  @NotNull
  @JsonProperty("id")
  Integer id;

  @NotNull
  @JsonProperty("interval")
  Integer interval;
}
