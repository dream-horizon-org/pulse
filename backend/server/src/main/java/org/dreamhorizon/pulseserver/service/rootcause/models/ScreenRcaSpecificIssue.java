package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaSpecificIssue {
  @JsonProperty("group_id")
  private String groupId;
  private String issue;
  private Long count;
  private Double avgDurationMs;
  @JsonProperty("thread_name")
  private String threadName;
}
