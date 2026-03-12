package org.dreamhorizon.pulseserver.dao.tenant.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {
  private String tenantId;
  private String name;
  private String description;
  private Integer tierId;
  private Boolean isActive;
  private String createdAt;
  private String updatedAt;
  private String gcpTenantId;
  private String domainName;
}
