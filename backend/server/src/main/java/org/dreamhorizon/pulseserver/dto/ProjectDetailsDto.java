package org.dreamhorizon.pulseserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for detailed project information.
 * Includes API key (only visible to admins).
 */
@Data
@Builder
public class ProjectDetailsDto {
    @JsonProperty("project_id")
    private String projectId;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("api_key")
    private String apiKey;  // Only included for admins
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("created_by")
    private String createdBy;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("user_role")
    private String userRole;  // Current user's role in this project
}
