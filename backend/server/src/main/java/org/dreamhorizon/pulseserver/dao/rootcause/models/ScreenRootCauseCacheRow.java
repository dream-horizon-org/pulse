package org.dreamhorizon.pulseserver.dao.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from otel.screen_root_cause_cache (aligned with {@link RootCauseCacheRow} / interaction RCA). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRootCauseCacheRow {

  @JsonProperty("ProjectId")
  private String projectId;

  @JsonProperty("screen_name")
  private String screenName;

  /** Anchor UTC calendar date (same semantics as interaction {@code root_cause_cache.date}). */
  private LocalDate date;

  @JsonProperty("window_end_utc")
  private LocalDateTime windowEndUtc;

  private String mode;
  private String baseline;
  private String segments;

  @JsonProperty("cached_at")
  private LocalDateTime cachedAt;
}
