package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Algorithm output: mode, baseline, segments (with metrics and deltas). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RootCauseResult {
  public static final String MODE_HIERARCHICAL = "hierarchical";
  public static final String MODE_FLAT = "flat";

  private String mode;
  private Map<String, Double> baseline;
  private List<RootCauseSegment> segments;
  /** When served from cache. */
  private String cachedAt;
  /** Total problematic count (error OR poor). */
  private Long totalProblematicCount;
  /** When total problematic = 0. */
  private Boolean everythingGood;
  /** When no data for tenant/project. */
  private Boolean noDataAvailable;

  @Data
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RootCauseSegment {
    private String label;
    private Map<String, String> dimensions;
    private Map<String, Double> metrics;
    private Map<String, Double> deltas;
  }
}
