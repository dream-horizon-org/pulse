package org.dreamhorizon.pulseserver.service.rootcause.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaSpecificIssue {
  private String groupId;
  private String issue;
  private Long count;
  private Double avgDurationMs;
  private String threadName;
}
