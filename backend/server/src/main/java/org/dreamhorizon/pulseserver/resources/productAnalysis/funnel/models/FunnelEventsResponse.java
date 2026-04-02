package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET {@code /v1/funnel/events} — matches funnel builder event pickers.
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

  /**
   * Distinct catalog filter keys for the project, excluding {@code EVENT}.
   */
  private List<String> filterKeys;
}
