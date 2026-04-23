package org.dreamhorizon.pulseserver.resources.productAnalysis.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET {@code /v1/funnels/events} — event names for funnel builder step pickers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelEventsResponse {

  /**
   * Custom event names ({@code FilterKey = EVENT}).
   */
  private List<String> events;
}
