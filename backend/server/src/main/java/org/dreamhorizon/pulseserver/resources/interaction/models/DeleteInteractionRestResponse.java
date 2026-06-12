package org.dreamhorizon.pulseserver.resources.interaction.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteInteractionRestResponse {
  @JsonProperty("status")
  private Integer status;
}
