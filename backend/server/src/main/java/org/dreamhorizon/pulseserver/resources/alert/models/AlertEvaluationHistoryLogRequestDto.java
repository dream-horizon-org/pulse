package org.dreamhorizon.pulseserver.resources.alert.models;

import org.dreamhorizon.pulseserver.enums.AlertState;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvaluationHistoryLogRequestDto {

  @NotNull
  @JsonProperty("alert_id")
  Integer alert_id;

  @NotNull
  @JsonProperty("reading")
  String reading;

  @NotNull
  @JsonProperty("successInteractionCount")
  Integer successInteractionCount;

  @NotNull
  @JsonProperty("errorInteractionCount")
  Integer errorInteractionCount;

  @NotNull
  @JsonProperty("totalInteractionCount")
  Integer totalInteractionCount;

  @NotNull
  @JsonProperty("evaluation_time")
  Long evaluation_time;

  @NotNull
  @JsonProperty("current_state")
  AlertState current_state;

  @NotNull
  @JsonProperty("threshold")
  Float threshold;

  @JsonProperty("minSuccessInteractions")
  Integer minSuccessInteractions;

  @JsonProperty("minErrorInteractions")
  Integer minErrorInteractions;

  @JsonProperty("minTotalInteractions")
  Integer minTotalInteractions;
}
