package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EvaluationHistoryEntryDto {
  @JsonProperty("evaluation_id")
  private Integer evaluationId;

  @JsonProperty("evaluation_result")
  private String evaluationResult;

  @JsonProperty("state")
  private AlertState state;

  @JsonProperty("evaluated_at")
  private Timestamp evaluatedAt;
}

