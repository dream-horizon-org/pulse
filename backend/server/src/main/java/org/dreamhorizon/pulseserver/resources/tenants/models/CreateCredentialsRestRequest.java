package org.dreamhorizon.pulseserver.resources.tenants.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCredentialsRestRequest {
  @NotBlank(message = "clickhousePassword is required")
  @Size(min = 8, max = 128, message = "clickhousePassword must be 8-128 characters")
  private String clickhousePassword;
}
