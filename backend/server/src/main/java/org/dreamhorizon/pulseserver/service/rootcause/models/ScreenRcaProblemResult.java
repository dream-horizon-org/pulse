package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaProblemResult {
  @JsonProperty("problem_type")
  private String problemType;
  private int rank;
  private double weightage;
  @JsonProperty("most_affected_segment")
  private String topSegment;
  /** Dimension filters for most-affected segment; used for session evidence queries. */
  @JsonIgnore
  private Map<String, String> segmentDimensions;
  private ScreenRcaMetrics metrics;
  @JsonProperty("segment_metrics")
  private ScreenRcaMetrics segmentMetrics;
  @JsonProperty("specific_issues")
  private List<ScreenRcaSpecificIssue> specificIssues;
  @JsonProperty("metric_id")
  private String metricId;
  @JsonIgnore
  private Long affectedUserCount;
  @JsonIgnore
  private int typePriorityOrdinal;
}
