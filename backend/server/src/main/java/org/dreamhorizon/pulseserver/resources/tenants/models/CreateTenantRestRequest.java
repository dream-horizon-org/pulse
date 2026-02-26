package org.dreamhorizon.pulseserver.resources.tenants.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRestRequest {
  @NotBlank(message = "tenantId is required")
  @Pattern(regexp = "^[a-zA-Z0-9_]{3,64}$", message = "tenantId must be 3-64 alphanumeric characters or underscores")
  private String tenantId;

  @NotBlank(message = "name is required")
  @Size(max = 255, message = "name must be less than 255 characters")
  private String name;

  @Size(max = 1000, message = "description must be less than 1000 characters")
  private String description;

  @NotBlank(message = "gcpTenantId is required")
  private String gcpTenantId;

  @NotBlank(message = "domainName is required")
  private String domainName;
}
