package org.dreamhorizon.pulseserver.dao.sessionrca.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from {@code otel.session_rca_snapshot}. Keyed by (ProjectId, date) — project-wide. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRcaCacheRow {

  @JsonProperty("ProjectId")
  private String projectId;

  private LocalDate date;

  @JsonProperty("window_end_utc")
  private LocalDateTime windowEndUtc;

  private String mode;
  private String baseline;
  private String segments;

  @JsonProperty("cached_at")
  private LocalDateTime cachedAt;
}
