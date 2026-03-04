package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusRestResponse {
  private Boolean success;
  private String message;
}

