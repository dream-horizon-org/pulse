package org.dreamhorizon.pulseserver.resources.v1.projects.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.util.serialization.Trimmed;

/**
 * Request model for creating a new project.
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
    
    @Trimmed
    @NotBlank(message = "Project name is required")
    @Size(min = 3, max = 30, message = "Project name must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9- ]+$", message = "Project name can only contain alphanumeric characters, hyphens, and spaces")
    private String name;
    
    @Trimmed
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}
