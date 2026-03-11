package org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialsResponse {
  private String projectId;
  private String clickhouseUsername;
  private Boolean isActive;
  private String createdAt;
  private String message;
}
