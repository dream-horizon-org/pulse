package org.dreamhorizon.pulseserver.resources.funnel.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStepHealth {
  private int stepLevel;
  private String stepName;
  private long totalUsers;
  private long crashUsers;
  private long anrUsers;
  private long nonFatalUsers;
  private double crashRate;
  private double anrRate;
  private double nonFatalRate;
}
