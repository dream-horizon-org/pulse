package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Request body for {@code POST /v1/admin/tenants}.
 * Caller identity (ownerId) is resolved from the JWT — never accepted from the request body.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateAdminTenantRequest {
  private String tenantName;
  private String projectName;
  private String description;
}
