package org.dreamhorizon.pulseserver.resources.v1.onboarding.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.util.serialization.Trimmed;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {
    
    @Trimmed
    @NotBlank(message = "Organization name is required")
    @Size(min = 3, max = 50, message = "Organization name must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9- ]+$", message = "Organization name can only contain alphanumeric characters, hyphens, and spaces")
    @JsonProperty("organizationName")
    private String organizationName;
    
    @Trimmed
    @NotBlank(message = "Project name is required")
    @Size(min = 3, max = 30, message = "Project name must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9- ]+$", message = "Project name can only contain alphanumeric characters, hyphens, and spaces")
    @JsonProperty("projectName")
    private String projectName;
    
    @Trimmed
    @JsonProperty("projectDescription")
    private String projectDescription;
}
