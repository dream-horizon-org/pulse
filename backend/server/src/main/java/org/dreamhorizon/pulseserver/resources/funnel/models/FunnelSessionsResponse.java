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
public class FunnelSessionsResponse {
  private int stepLevel;
  private String stepName;
  private long totalAffectedSessions;
  private List<FunnelSessionDetail> sessions;
}
