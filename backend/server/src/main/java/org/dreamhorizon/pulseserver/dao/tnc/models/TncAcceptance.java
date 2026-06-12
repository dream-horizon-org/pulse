package org.dreamhorizon.pulseserver.dao.tnc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TncAcceptance {
  private Long id;
  private String tenantId;
  private Long tncVersionId;
  private String acceptedByEmail;
  private String acceptedAt;
  private String userAgent;
}
