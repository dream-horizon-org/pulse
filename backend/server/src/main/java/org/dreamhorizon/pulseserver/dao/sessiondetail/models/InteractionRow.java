package org.dreamhorizon.pulseserver.dao.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractionRow {
  @JsonProperty("interaction_name")
  private String interactionName;
  @JsonProperty("success_count")
  private long successCount;
  @JsonProperty("failure_count")
  private long failureCount;
  @JsonProperty("avg_duration_ms")
  private double avgDurationMs;
  @JsonProperty("apdex_score")
  private double apdexScore;
}
