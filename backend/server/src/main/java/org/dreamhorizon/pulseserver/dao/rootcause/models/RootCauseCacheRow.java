package org.dreamhorizon.pulseserver.dao.rootcause.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from otel.root_cause_cache. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseCacheRow {

  private String projectId;
  private String interactionName;
  private LocalDate date;
  private String mode;
  private String baseline;
  private String segments;
  private LocalDateTime cachedAt;
}
