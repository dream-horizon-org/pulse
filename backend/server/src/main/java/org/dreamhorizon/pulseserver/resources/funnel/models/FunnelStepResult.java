package org.dreamhorizon.pulseserver.resources.funnel.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStepResult {
  private String stepName;
  private long count;
  private double conversionRate;
  private double dropoffRate;
}
