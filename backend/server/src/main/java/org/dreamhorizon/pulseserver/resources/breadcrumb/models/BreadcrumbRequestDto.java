package org.dreamhorizon.pulseserver.resources.breadcrumb.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbRequestDto {
  @NotBlank(message = "Session ID is required")
  private String sessionId;

  @NotBlank(message = "Error timestamp is required")
  private String errorTimestamp;
}
