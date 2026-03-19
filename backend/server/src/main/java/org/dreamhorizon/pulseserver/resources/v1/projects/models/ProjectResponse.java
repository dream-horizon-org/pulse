package org.dreamhorizon.pulseserver.resources.v1.projects.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for project operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    
    private String projectId;
    private String name;
    private String description;
    private String tenantId;
    private String apiKey;
    private String createdAt;
    private String createdBy;
}
