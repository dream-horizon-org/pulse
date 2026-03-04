package org.dreamhorizon.pulseserver.resources.v1.users.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProjectsResponse {
    
    @JsonProperty("projects")
    private List<ProjectSummary> projects;
    
    @JsonProperty("redirectTo")
    private String redirectTo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectSummary {
        @JsonProperty("projectId")
        private String projectId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("isActive")
        private Boolean isActive;
        
        @JsonProperty("role")
        private String role;
    }
}
