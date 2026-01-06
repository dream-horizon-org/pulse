package org.dreamhorizon.pulseserver.resources.athena.models;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQueryRequestDto {
  @NotBlank(message = "Query string is required")
  private String queryString;
  
  private List<String> parameters;
  
  private String timestamp;
}


