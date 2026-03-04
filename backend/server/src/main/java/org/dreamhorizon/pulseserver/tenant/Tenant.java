package org.dreamhorizon.pulseserver.tenant;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a tenant in the multi-tenant Pulse system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

  /**
   * Unique identifier for the tenant.
   */
  private String tenantId;

  /**
   * Display name of the tenant.
   */
  private String name;

  /**
   * Optional description of the tenant.
   */
  private String description;

  /**
   * Whether the tenant is active.
   */
  private boolean isActive;

  /**
   * Timestamp when the tenant was created.
   */
  private LocalDateTime createdAt;

  /**
   * Timestamp when the tenant was last updated.
   */
  private LocalDateTime updatedAt;


}

