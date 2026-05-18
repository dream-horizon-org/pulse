package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /v1/admin/tenants}.
 * Caller identity (ownerId) is resolved from the JWT — never accepted from the request body.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateAdminTenantRequest {

  @NotBlank(message = "Tenant name is required")
  @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters")
  private String tenantName;

  @NotBlank(message = "Project name is required")
  @Size(min = 3, max = 30, message = "Project name must be between 3 and 30 characters")
  private String projectName;

  @Size(max = 1000, message = "Project description must not exceed 1000 characters")
  private String projectDescription;
}

