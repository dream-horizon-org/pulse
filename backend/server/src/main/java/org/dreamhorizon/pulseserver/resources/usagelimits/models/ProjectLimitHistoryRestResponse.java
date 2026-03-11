package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * History response for a project's usage limit changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectLimitHistoryRestResponse {
  private String projectId;
  private List<ProjectUsageLimitRestResponse> history;
  private Integer totalCount;
}

