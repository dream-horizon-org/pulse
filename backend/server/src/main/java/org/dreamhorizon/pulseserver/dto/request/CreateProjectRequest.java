package org.dreamhorizon.pulseserver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new project.
 * 
 * <p><b>Note on tenantId:</b> The tenant ID is NOT included in this request for security reasons.
 * Instead, it is:
 * <ul>
 *   <li>Extracted from the authenticated user's JWT token (tenantId claim)</li>
 *   <li>Validated against the user's actual tenant membership in OpenFGA</li>
 * </ul>
 * 
 * <p>This approach ensures users can only create projects in tenants they belong to,
 * preventing security vulnerabilities where a user might attempt to create a project
 * in another tenant by manipulating the request body.
 * 
 * <p>The tenant context is implicitly provided through authentication, not explicitly
 * through the request payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {
    
    @NotBlank(message = "Project name is required")
    @Size(min = 3, max = 50, message = "Project name must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9 -]*[a-zA-Z0-9]$", 
             message = "Project name must start and end with alphanumeric characters and can only contain letters, numbers, spaces, and hyphens")
    @JsonProperty("name")
    private String name;
    
    @Size(max = 500, message = "Project description must not exceed 500 characters")
    @JsonProperty("description")
    private String description;
}
