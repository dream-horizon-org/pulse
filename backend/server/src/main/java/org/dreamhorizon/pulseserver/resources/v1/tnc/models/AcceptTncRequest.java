package org.dreamhorizon.pulseserver.resources.v1.tnc.models;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptTncRequest {
  @NotNull
  private Long versionId;
}
