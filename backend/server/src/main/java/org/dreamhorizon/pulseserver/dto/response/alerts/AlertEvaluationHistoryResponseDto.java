package org.dreamhorizon.pulseserver.dto.response.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.enums.AlertState;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertEvaluationHistoryResponseDto {
  @NotNull
  @JsonProperty("reading")
  String reading;

  @NotNull
  @JsonProperty("success_interaction_count")
  Integer successInteractionCount;

  @NotNull
  @JsonProperty("error_interaction_count")
  Integer errorInteractionCount;

  @NotNull
  @JsonProperty("total_interaction_count")
  Integer totalInteractionCount;

  @NotNull
  @JsonProperty("evaluation_time")
  Float evaluationTime;

  @NotNull
  @JsonProperty("evaluated_at")
  Timestamp evaluatedAt;

  @NotNull
  @JsonProperty("current_state")
  AlertState currentState;

  @NotNull
  @JsonProperty("min_success_interactions")
  Integer minSuccessInteractions;

  @NotNull
  @JsonProperty("min_error_interactions")
  Integer minErrorInteractions;

  @NotNull
  @JsonProperty("min_total_interactions")
  Integer minTotalInteractions;

  @NotNull
  @JsonProperty("threshold")
  Float threshold;
}
