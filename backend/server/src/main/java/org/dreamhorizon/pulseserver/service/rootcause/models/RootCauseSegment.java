package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RootCauseSegment {

  /** Hierarchical e.g. "Android + App 3.4.5 + Jio", flat e.g. "Platform: Android". */
  private String label;
  /** Dimension name -> value for this segment. */
  private Map<String, String> dimensions;
  /** Metric name -> value (volume, apdex, error_rate, ...). */
  private Map<String, Object> metrics;
  /** Metric name -> delta % (e.g. +15.5 for 15.5% worse than baseline). */
  private Map<String, Double> deltas;
}
