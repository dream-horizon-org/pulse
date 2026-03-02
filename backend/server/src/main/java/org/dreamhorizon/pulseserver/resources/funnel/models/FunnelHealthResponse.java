package org.dreamhorizon.pulseserver.resources.funnel.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelHealthResponse {
  private List<FunnelStepHealth> steps;
  private long totalCrashUsers;
  private long totalAnrUsers;
  private long totalNonFatalUsers;
}
