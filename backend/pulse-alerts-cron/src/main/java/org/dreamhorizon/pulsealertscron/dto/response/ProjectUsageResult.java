package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageResult {
  private String projectId;
  
  private Integer sessionFinalThreshold;
  private Long sessionsUsed;
  private Long sessionsRemaining;
  
  private Integer eventFinalThreshold;
  private Long eventsUsed;
  private Long eventsRemaining;
  
  private Long updatedAt;
  
  @Override
  public String toString() {
    return String.format(
        "ProjectUsageResult{projectId='%s', sessions=%d/%d (%d remaining), events=%d/%d (%d remaining)}", 
        projectId,
        sessionsUsed, sessionFinalThreshold, sessionsRemaining,
        eventsUsed, eventFinalThreshold, eventsRemaining
    );
  }
}
