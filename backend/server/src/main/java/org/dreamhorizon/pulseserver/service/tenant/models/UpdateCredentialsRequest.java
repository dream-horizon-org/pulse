package org.dreamhorizon.pulseserver.service.tenant.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCredentialsRequest {
  private String tenantId;
  private String newPassword;
  private String reason;
}
