package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickhouseProjectCredentialAudit {
  private Long id;
  private String projectId;
  private String action;
  private String performedBy;
  private String details;
  private String createdAt;
}
