package org.dreamhorizon.pulseserver.resources.performance.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class PerformanceMetricDistributionReq {
  @NotNull
  private Long startTime;
  @NotNull
  private Long endTime;
  @NotNull
  @NotEmpty
  private List<Group> groupBy;
  private PerformanceMetric metric;
  @Builder.Default
  private Map<String, List<String>> filters = Collections.emptyMap();
}
