package org.dreamhorizon.pulseserver.resources.v1.tnc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishTncResponse {
  private Long versionId;
  private String version;
  private String status;
  private String message;
}
