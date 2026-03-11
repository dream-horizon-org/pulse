package org.dreamhorizon.pulseserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for project summary information.
 * Used in list views and user project selection.
 */
@Data
@Builder
public class ProjectSummaryDto {
    @JsonProperty("project_id")
    private String projectId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("role")
    private String role;  // admin, member, viewer, or none
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("created_at")
    private String createdAt;
}
