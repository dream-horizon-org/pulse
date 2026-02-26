package org.dreamhorizon.pulseserver.resources.tenants.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogRestResponse {
  private Long id;
  private String tenantId;
  private String action;
  private String performedBy;
  private String details;
  private String createdAt;
}
