package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Greedy most-visited path derived from {@code otel.journey_results} rows.
 *
 * <p>Steps are ordered entry → anchor for {@code END} journeys and anchor → exit for
 * {@code START} journeys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyTopPathResponse {

  private List<JourneyTopPathStep> steps;

  /** {@code true} when at least two steps were derived without a mid-path gap. */
  private boolean complete;

  /** Users who hit the anchor (ENTRY → anchor edge when present). */
  private long anchorTraffic;

  /** Minimum edge traffic along the derived path (bottleneck). */
  private long pathTraffic;

  /** Set when {@code complete} is {@code false}. */
  private String incompletenessReason;
}
