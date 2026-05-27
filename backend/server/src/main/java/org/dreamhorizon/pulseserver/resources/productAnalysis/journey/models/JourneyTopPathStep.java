package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One step on the greedy most-visited path derived from journey results. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyTopPathStep {

  /** Depth position from journey compute ({@code 0} = anchor). */
  private int position;

  /** Screen or event name (without depth suffix). */
  private String stepName;

  /** Users on the edge leading into this step. */
  private long traffic;
}
