package org.dreamhorizon.pulseserver.service.tenant.dto;

import lombok.Data;

/**
 * Result of an atomic tenant + project creation.
 * Used by {@code TenantService.createTenantWithProject} — the shared provisioning method
 * called by both the onboarding flow and the admin dashboard endpoint.
 */
@Data
public class TenantWithProjectResult {
  private String tenantId;
  private String projectId;
  private String rawApiKey;
}
