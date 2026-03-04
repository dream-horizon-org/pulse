package org.dreamhorizon.pulseserver.resources.v1.onboarding.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {
    
    @NotBlank(message = "Organization name is required")
    @JsonProperty("organizationName")
    private String organizationName;
    
    @NotBlank(message = "Project name is required")
    @JsonProperty("projectName")
    private String projectName;
    
    @JsonProperty("projectDescription")
    private String projectDescription;
}
