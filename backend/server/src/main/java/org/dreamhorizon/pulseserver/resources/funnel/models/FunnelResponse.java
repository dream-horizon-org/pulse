package org.dreamhorizon.pulseserver.resources.funnel.models;

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
public class FunnelResponse {
  private List<FunnelStepResult> steps;
  private long totalEnteredUsers;
  private double overallConversionRate;
  private Map<String, List<FunnelStepResult>> groupedResults;
}
