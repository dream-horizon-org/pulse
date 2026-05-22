package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DegradingInteraction {

  /** Interaction name — SpanName from otel_traces where PulseType = 'interaction'. */
  private String interactionName;
  /** Total occurrences of this interaction in bad sessions of the segment. */
  private long interactionCount;
  /** Average apdex score for this interaction across bad sessions. */
  private double avgApdex;
  /**
   * Total apdex points lost: count − sum(apdexScores). Higher = more impact on session quality.
   */
  private double degradationWeight;
}
