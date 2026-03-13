package org.dreamhorizon.pulseserver.dao.rootcause.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseCacheRow {
  private String tenantId;
  private String projectId;
  private String interactionName;
  private String date;
  private String mode;
  private String baseline;
  private String segments;
  private Instant cachedAt;
}
