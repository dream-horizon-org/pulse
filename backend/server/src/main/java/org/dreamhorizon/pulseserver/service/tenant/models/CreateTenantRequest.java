package org.dreamhorizon.pulseserver.service.tenant.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {
  private String tenantId;
  private String name;
  private String description;
  private String clickhousePassword;
  private String gcpTenantId;
  private String domainName;
}
