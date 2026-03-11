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
  private String tenant;
  private Long eventsUsed;
  private Long sessionsUsed;
  
  @Override
  public String toString() {
    return String.format("UsageStats{tenant='%s', events=%d, sessions=%d}", 
        tenant, eventsUsed, sessionsUsed);
  }
}
