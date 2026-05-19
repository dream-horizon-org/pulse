package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaProblemResult {
  private String problemType;
  private int rank;
  private double weightage;
  private String topSegment;
  private ScreenRcaMetrics metrics;
  private List<ScreenRcaSpecificIssue> specificIssues;
  private String metricId;
  @JsonIgnore
  private Long affectedUserCount;
  @JsonIgnore
  private int typePriorityOrdinal;
}
