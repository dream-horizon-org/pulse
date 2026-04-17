package org.dreamhorizon.pulseserver.resources.tenants.models;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantTierRestRequest {
  @NotNull
  private Integer tierId;
}
