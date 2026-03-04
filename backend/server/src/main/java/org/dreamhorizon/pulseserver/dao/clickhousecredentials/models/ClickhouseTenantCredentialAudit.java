package org.dreamhorizon.pulseserver.dao.clickhousecredentials.models;

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
  private String projectId;
  private String action;
  private String performedBy;
  private String details;
  private String createdAt;
}
