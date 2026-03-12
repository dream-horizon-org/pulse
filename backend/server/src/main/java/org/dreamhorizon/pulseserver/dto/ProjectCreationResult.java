package org.dreamhorizon.pulseserver.dto;

import lombok.Builder;
import lombok.Data;
import org.dreamhorizon.pulseserver.dao.project.models.Project;

/**
 * Result of project creation operation.
 * Contains the created project along with the raw API key
 * (which is only available at creation time before encryption).
 */
@Data
@Builder
public class ProjectCreationResult {
  private final Project project;
  private final String rawApiKey;
}
