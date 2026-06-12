package org.dreamhorizon.pulseserver.service.tenant.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInfo {
  private String tenantId;
  private String name;
  private String description;
  private String clickhouseUsername;
  private String clickhousePassword;
  private Boolean isActive;
  private String createdAt;
  private String updatedAt;
  private String message;
}
