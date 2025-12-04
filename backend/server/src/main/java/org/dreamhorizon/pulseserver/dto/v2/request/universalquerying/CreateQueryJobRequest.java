package org.dreamhorizon.pulseserver.dto.v2.request.universalquerying;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateQueryJobRequest {
  @JsonProperty("query")
  @NotBlank(message = "query cannot be blank")
  private String query;
}
