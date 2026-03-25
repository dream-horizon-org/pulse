package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStats {
  private String project_id;
  private Long eventsUsed;
  private Long sessionsUsed;
  
  @Override
  public String toString() {
    return String.format("UsageStats{project_id='%s', events=%d, sessions=%d}", 
        project_id, eventsUsed, sessionsUsed);
  }
}
