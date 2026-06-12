package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * List response for project usage limits (internal endpoints).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimitListRestResponse {
  private List<ProjectUsageLimitRestResponse> limits;
  private Integer totalCount;
}

