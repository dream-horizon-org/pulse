package org.dreamhorizon.pulseserver.service.tenant.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCredentialsRequest {
  private String tenantId;
  private String clickhousePassword;
}
