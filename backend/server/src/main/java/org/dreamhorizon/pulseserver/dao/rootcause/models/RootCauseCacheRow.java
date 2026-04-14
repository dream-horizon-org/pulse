package org.dreamhorizon.pulseserver.dao.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("ProjectId")
  private String projectId;

  @JsonProperty("interaction_name")
  private String interactionName;

  private LocalDate date;

  @JsonProperty("window_end_utc")
  private LocalDateTime windowEndUtc;

  private String mode;
  private String baseline;
  private String segments;

  @JsonProperty("error_attribution_json")
  private String errorAttributionJson;

  @JsonProperty("cached_at")
  private LocalDateTime cachedAt;
}
