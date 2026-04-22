package org.dreamhorizon.pulseserver.dao.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from otel.screen_root_cause_cache. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRootCauseCacheRow {

  @JsonProperty("ProjectId")
  private String projectId;

  @JsonProperty("screen_name")
  private String screenName;

  @JsonProperty("window_start_date")
  private LocalDate windowStartDate;

  @JsonProperty("window_end_date")
  private LocalDate windowEndDate;

  @JsonProperty("window_start_utc")
  private LocalDateTime windowStartUtc;

  @JsonProperty("window_end_utc")
  private LocalDateTime windowEndUtc;

  /** Full {@link org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult} JSON. */
  @JsonProperty("result_json")
  private String resultJson;

  @JsonProperty("cached_at")
  private LocalDateTime cachedAt;
}
