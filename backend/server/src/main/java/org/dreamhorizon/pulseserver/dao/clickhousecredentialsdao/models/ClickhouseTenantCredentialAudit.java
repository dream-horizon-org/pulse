package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickhouseTenantCredentialAudit {
  private Long id;
  private String tenantId;
  private String action;
  private String performedBy;
  private String details;
  private String createdAt;
}
