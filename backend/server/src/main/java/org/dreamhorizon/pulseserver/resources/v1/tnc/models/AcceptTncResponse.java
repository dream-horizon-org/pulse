package org.dreamhorizon.pulseserver.resources.v1.tnc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptTncResponse {
  private String status;
  private String tenantId;
  private String version;
  private String acceptedBy;
  private String acceptedAt;
}
