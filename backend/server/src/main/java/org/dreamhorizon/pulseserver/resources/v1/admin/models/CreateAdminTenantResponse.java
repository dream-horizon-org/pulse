package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import lombok.Data;

/**
 * Response body for {@code POST /v1/admin/tenants}.
 * Contains the provisioned tenantId, projectId, and raw API key.
 * No JWT tokens are returned — the admin stays in their current session.
 */
@Data
public class CreateAdminTenantResponse {
  private String tenantId;
  private String projectId;
  private String apiKey;
}
