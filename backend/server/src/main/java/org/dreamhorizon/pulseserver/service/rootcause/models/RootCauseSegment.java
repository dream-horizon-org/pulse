package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
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
  /** Example session IDs demonstrating this segment's issues (2 most relevant). */
  @Default
  private List<String> exampleSessionIds = new ArrayList<>();

  /**
   * 1-based ordering hint for the RCA LLM: matches merged RCA segment order from the server ({@code GET}
   * {@code /root-cause} list order: hierarchical 2D+ first, then flat 1D, after cap and gate). Set only
   * on payloads sent to pulse_ai — not used for {@code GET /root-cause} cache rows.
   */
  private Integer serverRank;
}
