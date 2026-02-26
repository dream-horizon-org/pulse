package org.dreamhorizon.pulseserver.resources.tenants.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialsRestResponse {
  private String tenantId;
  private String clickhouseUsername;
  private String clickhousePassword;
  private Boolean isActive;
  private String message;
  private String createdAt;
  private String updatedAt;
}
