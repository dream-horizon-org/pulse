package org.dreamhorizon.pulseserver.resources.query.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQueryRequestDto {
  @NotBlank(message = "Query string is required")
  private String queryString;
}

