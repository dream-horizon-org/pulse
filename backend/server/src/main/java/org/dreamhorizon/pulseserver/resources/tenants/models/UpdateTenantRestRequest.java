package org.dreamhorizon.pulseserver.resources.tenants.models;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRestRequest {
  @Size(max = 255, message = "name must be less than 255 characters")
  private String name;

  @Size(max = 1000, message = "description must be less than 1000 characters")
  private String description;
}
